package com.ktb.moyeota.global.security.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.security.AuthProperties;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

class AuthCookiesTest {

    private static final String ORIGIN = "http://localhost:8080";
    private static final URI CALLBACK = URI.create(ORIGIN + "/api/auth/kakao/callback");

    private final AuthCookies authCookies = new AuthCookies(new AuthProperties(
            new AuthProperties.Jwt("0123456789abcdef0123456789abcdef", "moyeota", Duration.ofMinutes(30)),
            new AuthProperties.Refresh(Duration.ofDays(7), Duration.ofSeconds(10)),
            new AuthProperties.Signup(Duration.ofMinutes(15)),
            new AuthProperties.Cookie(false)));

    @ParameterizedTest
    @ValueSource(strings = {"/api/users", "/api/users/nickname-availability", "/api/images/presigned-url"})
    @DisplayName("회원가입 쿠키는 회원가입 세션으로 인증하는 모든 경로의 요청에 실린다")
    void signupCookieReachesEverySignupPath(String path) throws IOException {
        CookieManager browser = browserWith(authCookies.signupToken("signup-value", Duration.ofMinutes(15)));

        assertThat(cookieNamesSentTo(browser, path)).contains(AuthCookies.SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("만료 쿠키는 발급한 회원가입 쿠키와 같은 경로라서 실제로 지운다")
    void expiredSignupCookieRemovesIssuedOne() throws IOException {
        CookieManager browser = browserWith(authCookies.signupToken("signup-value", Duration.ofMinutes(15)));

        browser.put(CALLBACK, setCookie(authCookies.expiredSignupToken()));

        assertThat(cookieNamesSentTo(browser, "/api/images/presigned-url")).doesNotContain(AuthCookies.SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("리프레시 쿠키는 /auth 밖의 요청에는 실리지 않는다")
    void refreshCookieStaysUnderAuth() throws IOException {
        CookieManager browser = browserWith(authCookies.refreshToken("refresh-value", Duration.ofDays(7)));

        assertThat(cookieNamesSentTo(browser, "/api/auth/tokens")).contains(AuthCookies.REFRESH_TOKEN);
        assertThat(cookieNamesSentTo(browser, "/api/images/presigned-url")).doesNotContain(AuthCookies.REFRESH_TOKEN);
    }

    @Test
    @DisplayName("오리진 쿠키는 콜백 요청에 실리고 /auth 밖의 요청에는 실리지 않는다")
    void frontCookieReachesCallback() throws IOException {
        CookieManager browser = browserWith(authCookies.oauthFront("http://localhost:3000"));

        assertThat(cookieNamesSentTo(browser, "/api/auth/kakao/callback")).contains(AuthCookies.OAUTH_FRONT);
        assertThat(cookieNamesSentTo(browser, "/api/users")).doesNotContain(AuthCookies.OAUTH_FRONT);
    }

    private CookieManager browserWith(ResponseCookie cookie) throws IOException {
        CookieManager browser = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        browser.put(CALLBACK, setCookie(cookie));
        return browser;
    }

    private Map<String, List<String>> setCookie(ResponseCookie cookie) {
        return Map.of(HttpHeaders.SET_COOKIE, List.of(cookie.toString()));
    }

    private List<String> cookieNamesSentTo(CookieManager browser, String path) throws IOException {
        List<String> headers = browser.get(URI.create(ORIGIN + path), Map.of())
                .getOrDefault(HttpHeaders.COOKIE, List.of());
        return headers.stream()
                .flatMap(header -> Arrays.stream(header.split(";")))
                .map(pair -> pair.strip().split("=", 2)[0])
                .toList();
    }
}
