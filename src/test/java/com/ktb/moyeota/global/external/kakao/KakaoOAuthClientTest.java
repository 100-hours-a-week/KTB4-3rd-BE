package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import com.ktb.moyeota.domain.auth.error.OAuthLoginException;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class KakaoOAuthClientTest {

    private static final String CLIENT_ID = "test-kakao-client-id";
    private static final String CLIENT_SECRET = "test-kakao-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8080/auth/kakao/callback";
    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private static final String TOKEN_BODY = """
            {"token_type":"bearer","access_token":"kakao-access","expires_in":21599,
             "refresh_token":"kakao-refresh","refresh_token_expires_in":5183999}
            """;
    private static final String FULL_USER_BODY = """
            {"id":1234567890,"connected_at":"2026-09-20T00:00:00Z",
             "kakao_account":{
               "name":"홍길동","name_needs_agreement":false,
               "gender":"female","gender_needs_agreement":false,
               "profile":{"nickname":"길동이",
                          "profile_image_url":"http://k.kakaocdn.net/img.jpg",
                          "is_default_image":false}}}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoOAuthClient client = new KakaoOAuthClient(
            new KakaoProperties(
                    CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI),
            builder.build());

    @Nested
    @DisplayName("인가 URI 조립")
    class BuildAuthorizeUri {

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
            MultiValueMap<String, String> params = UriComponentsBuilder
                    .fromUri(client.buildAuthorizeUri("state-value")).build().getQueryParams();

            assertThat(params.keySet())
                    .containsExactlyInAnyOrder("client_id", "redirect_uri", "response_type", "state");
            assertThat(params.getFirst("client_id")).isEqualTo(CLIENT_ID);
            assertThat(params.getFirst("response_type")).isEqualTo("code");
            assertThat(params.getFirst("state")).isEqualTo("state-value");
            assertThat(URLDecoder.decode(params.getFirst("redirect_uri"), StandardCharsets.UTF_8))
                    .isEqualTo(REDIRECT_URI);
        }

        @Test
        @DisplayName("리다이렉트 URI는 퍼센트 인코딩되어 실린다")
        void encodesRedirectUri() {
            assertThat(client.buildAuthorizeUri("state-value").getRawQuery())
                    .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fkakao%2Fcallback");
        }

        @Test
        @DisplayName("인가 URI에는 클라이언트 시크릿이 실리지 않는다")
        void neverCarriesClientSecret() {
            assertThat(client.buildAuthorizeUri("state-value").toString())
                    .doesNotContain(CLIENT_SECRET);
        }
    }

    @Nested
    @DisplayName("프로필 조회")
    class FetchProfile {

        @Test
        @DisplayName("인가 코드를 토큰으로 바꾼 뒤 그 토큰으로 카카오 회원번호를 조회한다")
        void exchangesCodeThenFetchesUser() {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", CLIENT_ID);
            form.add("client_secret", CLIENT_SECRET);
            form.add("redirect_uri", REDIRECT_URI);
            form.add("code", "auth-code");
            server.expect(requestTo(TOKEN_URI))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().formData(form))
                    .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
            server.expect(requestTo(USER_INFO_URI))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access"))
                    .andRespond(withSuccess(FULL_USER_BODY, MediaType.APPLICATION_JSON));

            OAuthUserProfile profile = client.fetchProfile("auth-code");

            server.verify();
            assertThat(profile).isEqualTo(new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890"));
        }

        @Test
        @DisplayName("동의항목이 하나도 없어 회원번호만 와도 프로필을 만든다")
        void idOnlyResponse() {
            givenToken();
            givenUser("{\"id\":77}");

            assertThat(client.fetchProfile("auth-code"))
                    .isEqualTo(new OAuthUserProfile(OAuthProvider.KAKAO, "77"));
        }

        @Test
        @DisplayName("토큰 교환 중 카카오 5xx는 OAUTH_UNAVAILABLE이다")
        void tokenServerErrorIsUnavailable() {
            server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

            assertFailsWith(OAuthLoginError.OAUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("타임아웃·연결 실패는 OAUTH_UNAVAILABLE이다")
        void ioFailureIsUnavailable() {
            server.expect(requestTo(TOKEN_URI)).andRespond(request -> {
                throw new IOException("Read timed out");
            });

            assertFailsWith(OAuthLoginError.OAUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("토큰 응답에 access_token이 없으면 OAUTH_UNAVAILABLE이다")
        void tokenWithoutAccessTokenIsUnavailable() {
            server.expect(requestTo(TOKEN_URI))
                    .andRespond(withSuccess("{\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

            assertFailsWith(OAuthLoginError.OAUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("프로필 조회 실패는 상태 코드와 무관하게 OAUTH_UNAVAILABLE이다")
        void userInfoFailureIsUnavailable() {
            givenToken();
            server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertFailsWith(OAuthLoginError.OAUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("프로필 응답에 회원번호가 없으면 OAUTH_UNAVAILABLE이다")
        void userWithoutIdIsUnavailable() {
            givenToken();
            givenUser("{\"kakao_account\":{}}");

            assertFailsWith(OAuthLoginError.OAUTH_UNAVAILABLE);
        }

        private void givenToken() {
            server.expect(requestTo(TOKEN_URI))
                    .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        }

        private void givenUser(String body) {
            server.expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private void assertFailsWith(OAuthLoginError expected) {
            assertThatThrownBy(() -> client.fetchProfile("auth-code"))
                    .isInstanceOfSatisfying(OAuthLoginException.class,
                            e -> assertThat(e.getError()).isEqualTo(expected));
        }
    }
}
