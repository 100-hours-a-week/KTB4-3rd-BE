package com.ktb.moyeota.global.external.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiAnalysisClientTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String TAXI_METER = BASE_URL + "/taxi_meter";
    private static final String IMAGE_URL =
            "https://moyeota-dev-images.s3.ap-northeast-2.amazonaws.com/meter/a.jpg?X-Amz-Expires=1800";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AiAnalysisClient client = new AiAnalysisClient(builder.build());

    @Test
    @DisplayName("금액을 읽으면 그 금액을 돌려준다")
    void readsCost() {
        server.expect(requestTo(TAXI_METER))
                .andRespond(withSuccess("{\"success\":true,\"cost\":18000}", MediaType.APPLICATION_JSON));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.Read<>(18000));
    }

    @Test
    @DisplayName("사진 주소를 image_URL 키에 담아 POST /taxi_meter 로 보낸다")
    void sendsImageUrlKey() {
        server.expect(requestTo(TAXI_METER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"image_URL\":\"" + IMAGE_URL + "\"}", true))
                .andRespond(withSuccess("{\"success\":true,\"cost\":3000}", MediaType.APPLICATION_JSON));

        client.readMeter(IMAGE_URL);

        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"JUDGE_PARSE_FAILED", "IMAGE_SIZE_INVALID", "IMAGE_TYPE_INVALID"})
    @DisplayName("사진 때문에 읽지 못했다는 422는 사진 문제다")
    void unreadable(String code) {
        server.expect(requestTo(TAXI_METER)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(code)));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.Unreadable<>(code));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "422, IMAGE_FETCH_FAILED",
            "422, REQUEST_INVALID",
            "502, UPSTREAM_FAILED",
            "503, MODEL_NOT_READY",
            "500, INTERNAL"})
    @DisplayName("사진이 아닌 이유로 실패한 응답은 시스템 문제이고 오류 코드를 남긴다")
    void systemFailure(int status, String code) {
        server.expect(requestTo(TAXI_METER)).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(code)));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.SystemFailure<>(code));
    }

    @Test
    @DisplayName("명세에 없는 오류 코드는 시스템 문제로 보고 받은 코드를 그대로 남긴다")
    void unknownErrorCode() {
        server.expect(requestTo(TAXI_METER)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody("NEW_CODE")));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.SystemFailure<>("NEW_CODE"));
    }

    @Test
    @DisplayName("앞단 프록시가 JSON이 아닌 오류를 주면 HTTP 상태로 시스템 문제를 남긴다")
    void proxyError() {
        server.expect(requestTo(TAXI_METER)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                .contentType(MediaType.TEXT_HTML)
                .body("<html><body>504 Gateway Time-out</body></html>"));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.SystemFailure<>("HTTP_504"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"success\":false,\"cost\":3000}", "{\"success\":true}", "{}", "null"})
    @DisplayName("200인데 명세와 다른 본문이면 시스템 문제다")
    void invalidSuccessBody(String body) {
        server.expect(requestTo(TAXI_METER)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.readMeter(IMAGE_URL))
                .isEqualTo(new AnalysisOutcome.SystemFailure<>(AiAnalysisClient.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("200인데 본문을 해석할 수 없으면 시스템 문제다")
    void unreadableSuccessBody() {
        server.expect(requestTo(TAXI_METER)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThat(client.readMeter(IMAGE_URL))
                .isEqualTo(new AnalysisOutcome.SystemFailure<>(AiAnalysisClient.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("응답 시간이 초과되면 시스템 문제다")
    void timeout() {
        server.expect(requestTo(TAXI_METER)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.readMeter(IMAGE_URL))
                .isEqualTo(new AnalysisOutcome.SystemFailure<>(AiAnalysisClient.NETWORK_ERROR));
    }

    @Test
    @DisplayName("금액의 범위는 판단하지 않고 받은 그대로 돌려준다")
    void passesCostAsIs() {
        server.expect(requestTo(TAXI_METER))
                .andRespond(withSuccess("{\"success\":true,\"cost\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.readMeter(IMAGE_URL)).isEqualTo(new AnalysisOutcome.Read<>(0));
    }

    private static String errorBody(String code) {
        return "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"model output is not valid\"}";
    }
}
