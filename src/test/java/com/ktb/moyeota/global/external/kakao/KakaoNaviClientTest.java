package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoNaviClientTest {

    private static final String DIRECTIONS_URI = "https://apis-navi.kakaomobility.com/v1/directions";
    private static final BigDecimal ORIGIN_LAT = new BigDecimal("37.394500");
    private static final BigDecimal ORIGIN_LNG = new BigDecimal("127.111200");
    private static final BigDecimal DEST_LAT = new BigDecimal("37.497900");
    private static final BigDecimal DEST_LNG = new BigDecimal("127.027600");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoNaviClient client = new KakaoNaviClient(
            new KakaoProperties("test-rest-key", "secret", "http://localhost/callback",
                    "https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token",
                    "https://kapi.kakao.com/v2/user/me"),
            new KakaoNaviProperties(DIRECTIONS_URI),
            builder.build());

    @Test
    @DisplayName("경로를 찾으면 요약의 소요 시간을 초 단위로 돌려준다")
    void returnsDuration() {
        server.expect(requestTo(startsWith(DIRECTIONS_URI)))
                .andRespond(withSuccess("""
                        {"routes":[{"result_code":0,"result_msg":"길찾기 성공",
                                    "summary":{"distance":17662,"duration":1059}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .contains(Duration.ofSeconds(1059));
    }

    @Test
    @DisplayName("좌표는 경도,위도 순서로 REST 키와 함께 보낸다")
    void sendsLngLatWithRestKey() {
        server.expect(requestTo(startsWith(DIRECTIONS_URI)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("origin", "127.111200,37.394500"))
                .andExpect(queryParam("destination", "127.027600,37.497900"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-key"))
                .andRespond(withSuccess("""
                        {"routes":[{"result_code":0,"summary":{"duration":60}}]}
                        """, MediaType.APPLICATION_JSON));

        client.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG);

        server.verify();
    }

    @Test
    @DisplayName("경로를 못 찾았다는 결과 코드면 비어 있다")
    void emptyWhenRouteNotFound() {
        server.expect(requestTo(startsWith(DIRECTIONS_URI)))
                .andRespond(withSuccess("""
                        {"routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5 m 이내로 설정된 경우 경로를 탐색할 수 없음"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG)).isEmpty();
    }

    @Test
    @DisplayName("카카오 서버가 실패하면 비어 있다")
    void emptyWhenServerFails() {
        server.expect(requestTo(startsWith(DIRECTIONS_URI))).andRespond(withServerError());

        assertThat(client.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG)).isEmpty();
    }
}
