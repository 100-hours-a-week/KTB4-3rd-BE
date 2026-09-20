package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

class KakaoOAuthClientTest {

    private static final String CLIENT_ID = "test-kakao-client-id";
    private static final String REDIRECT_URI = "http://localhost:8080/auth/kakao/callback";
    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";

    private final KakaoOAuthClient client =
            new KakaoOAuthClient(new KakaoProperties(CLIENT_ID, REDIRECT_URI, AUTHORIZE_URI));

    @Test
    @DisplayName("인가 URI는 카카오 인가 엔드포인트를 가리킨다")
    void pointsToAuthorizeEndpoint() {
        URI uri = client.buildAuthorizeUri("state-value");

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(uri.getPath()).isEqualTo("/oauth/authorize");
    }

    @Test
    @DisplayName("인가 URI에 필수 쿼리 네 개가 실린다")
    void carriesRequiredQueryParams() {
        MultiValueMap<String, String> params = queryParamsOf(client.buildAuthorizeUri("state-value"));

        assertThat(params.keySet())
                .containsExactlyInAnyOrder("client_id", "redirect_uri", "response_type", "state");
        assertThat(params.getFirst("client_id")).isEqualTo(CLIENT_ID);
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("state")).isEqualTo("state-value");
        assertThat(decode(params.getFirst("redirect_uri"))).isEqualTo(REDIRECT_URI);
    }

    @Test
    @DisplayName("리다이렉트 URI는 퍼센트 인코딩되어 실린다")
    void encodesRedirectUri() {
        URI uri = client.buildAuthorizeUri("state-value");

        assertThat(uri.getRawQuery())
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fkakao%2Fcallback");
    }

    private static MultiValueMap<String, String> queryParamsOf(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
