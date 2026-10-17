package com.ktb.moyeota.global.external.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

class AiAnalysisClientTransferTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String RECEIPT = BASE_URL + "/receipt";
    private static final String IMAGE_URL =
            "https://moyeota-dev-images.s3.ap-northeast-2.amazonaws.com/transfer/a.jpg?X-Amz-Expires=1800";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AiAnalysisClient client = new AiAnalysisClient(builder.build());

    @Test
    @DisplayName("송금 사진에서 읽은 금액을 돌려주고, 보낸 사람 · 보낸 시각은 쓰지 않는다")
    void readsAmount() {
        server.expect(requestTo(RECEIPT)).andRespond(withSuccess(
                "{\"success\":true,\"amount_cost\":15400,\"sender_name\":\"홍길동\",\"send_time\":\"2026-09-23 12:30:45\"}",
                MediaType.APPLICATION_JSON));

        assertThat(client.readTransfer(IMAGE_URL)).isEqualTo(new AnalysisOutcome.Read<>(15400));
    }

    @Test
    @DisplayName("사진 주소를 image_URL 키에 담아 POST /receipt 로 보낸다")
    void sendsImageUrlKey() {
        server.expect(requestTo(RECEIPT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"image_URL\":\"" + IMAGE_URL + "\"}", true))
                .andRespond(withSuccess("{\"success\":true,\"amount_cost\":6000}", MediaType.APPLICATION_JSON));

        client.readTransfer(IMAGE_URL);

        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"JUDGE_PARSE_FAILED", "IMAGE_SIZE_INVALID", "IMAGE_TYPE_INVALID"})
    @DisplayName("이체 완료로 인정되지 않았거나 사진 때문에 읽지 못한 422는 사진 문제다")
    void unreadable(String code) {
        server.expect(requestTo(RECEIPT)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(code)));

        assertThat(client.readTransfer(IMAGE_URL)).isEqualTo(new AnalysisOutcome.Unreadable<>(code));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "422, IMAGE_FETCH_FAILED",
            "502, UPSTREAM_FAILED",
            "503, MODEL_NOT_READY",
            "500, INTERNAL"})
    @DisplayName("사진이 아닌 이유로 실패한 응답은 시스템 문제다")
    void systemFailure(int status, String code) {
        server.expect(requestTo(RECEIPT)).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(code)));

        assertThat(client.readTransfer(IMAGE_URL)).isEqualTo(new AnalysisOutcome.SystemFailure<>(code));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"success\":false,\"amount_cost\":6000}", "{\"success\":true}",
            "{\"success\":true,\"cost\":6000}"})
    @DisplayName("200인데 명세와 다른 본문이면 시스템 문제다")
    void invalidSuccessBody(String body) {
        server.expect(requestTo(RECEIPT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.readTransfer(IMAGE_URL))
                .isEqualTo(new AnalysisOutcome.SystemFailure<>(AiAnalysisClient.INVALID_RESPONSE));
    }

    private static String errorBody(String code) {
        return "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"model output is not valid\"}";
    }
}
