package com.ktb.moyeota.global.external.ai;

import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisClient {

    static final String NETWORK_ERROR = "NETWORK_ERROR";
    static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    private static final String TAXI_METER_PATH = "/taxi_meter";
    private static final String RECEIPT_PATH = "/receipt";

    private final RestClient aiRestClient;

    public AnalysisOutcome<Integer> readMeter(String imageUrl) {
        return analyze(TAXI_METER_PATH, imageUrl, AiMeterResponse.class, AiMeterResponse::cost);
    }

    public AnalysisOutcome<Integer> readTransfer(String imageUrl) {
        return analyze(RECEIPT_PATH, imageUrl, AiReceiptResponse.class, AiReceiptResponse::amountCost);
    }

    private <R extends AiSuccessResponse, T> AnalysisOutcome<T> analyze(
            String path, String imageUrl, Class<R> responseType, Function<R, T> resultOf) {
        try {
            R response = aiRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiImageRequest(imageUrl))
                    .retrieve()
                    .body(responseType);
            return toOutcome(path, response, resultOf);
        } catch (RestClientResponseException e) {
            return toOutcome(path, e);
        } catch (ResourceAccessException e) {
            log.warn("[AI_ANALYSIS_FAILED] AI 서버에 연결하지 못했습니다. path={}", path, e);
            return new AnalysisOutcome.SystemFailure<>(NETWORK_ERROR);
        } catch (RestClientException e) {
            log.error("[AI_ANALYSIS_FAILED] AI 응답을 읽지 못했습니다. path={}", path, e);
            return new AnalysisOutcome.SystemFailure<>(INVALID_RESPONSE);
        }
    }

    private static <R extends AiSuccessResponse, T> AnalysisOutcome<T> toOutcome(
            String path, R response, Function<R, T> resultOf) {
        T result = response != null && Boolean.TRUE.equals(response.success()) ? resultOf.apply(response) : null;
        if (result == null) {
            log.error("[AI_ANALYSIS_FAILED] AI 성공 응답이 명세와 다릅니다. path={} response={}", path, response);
            return new AnalysisOutcome.SystemFailure<>(INVALID_RESPONSE);
        }
        return new AnalysisOutcome.Read<>(result);
    }

    private static <T> AnalysisOutcome<T> toOutcome(String path, RestClientResponseException e) {
        String code = errorCodeOf(e);
        Optional<AiErrorCode> errorCode = AiErrorCode.fromCode(code);
        if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)
                && errorCode.filter(AiErrorCode::isUnreadable).isPresent()) {
            return new AnalysisOutcome.Unreadable<>(code);
        }
        String failure = code != null ? code : "HTTP_" + e.getStatusCode().value();
        if (errorCode.filter(AiErrorCode.REQUEST_INVALID::equals).isPresent()) {
            log.error("[AI_ANALYSIS_FAILED] AI 요청이 명세와 다릅니다. path={} status={}", path, e.getStatusCode().value());
        } else {
            log.warn("[AI_ANALYSIS_FAILED] AI 서버 오류. path={} status={} code={}",
                    path, e.getStatusCode().value(), failure);
        }
        return new AnalysisOutcome.SystemFailure<>(failure);
    }

    private static String errorCodeOf(RestClientResponseException e) {
        try {
            AiErrorResponse error = e.getResponseBodyAs(AiErrorResponse.class);
            return error == null ? null : error.code();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
