package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {

    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";
    private static final String ADMIN_KEY = "test-admin-key";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("어드민 키로 회원번호를 지정해 연결 끊기를 요청한다")
    void unlinkWithAdminKey() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", "1234567890");
        server.expect(requestTo(UNLINK_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"id\":1234567890}", MediaType.APPLICATION_JSON));

        client(ADMIN_KEY).unlink("1234567890");

        server.verify();
    }

    @Test
    @DisplayName("카카오가 실패해도 예외를 던지지 않는다")
    void failureDoesNotThrow() {
        server.expect(requestTo(UNLINK_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatCode(() -> client(ADMIN_KEY).unlink("1234567890")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("어드민 키가 없으면 요청하지 않는다")
    void skipWithoutAdminKey() {
        client(" ").unlink("1234567890");

        server.verify();
    }

    private KakaoUnlinkClient client(String adminKey) {
        return new KakaoUnlinkClient(new KakaoUnlinkProperties(UNLINK_URI, adminKey), builder.build());
    }
}
