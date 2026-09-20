package com.ktb.moyeota.global.security.cookie;

import com.ktb.moyeota.global.security.AuthProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String OAUTH_STATE = "oauth_state";

    private static final String REFRESH_TOKEN_PATH = "/auth";
    private static final String OAUTH_STATE_PATH = "/auth";
    private static final Duration OAUTH_STATE_MAX_AGE = Duration.ofMinutes(5);
    private static final String SAME_SITE = "Lax";

    private final AuthProperties authProperties;

    public ResponseCookie refreshToken(String value, Duration maxAge) {
        return builder(REFRESH_TOKEN, value, REFRESH_TOKEN_PATH).maxAge(maxAge).build();
    }

    public ResponseCookie expiredRefreshToken() {
        return builder(REFRESH_TOKEN, "", REFRESH_TOKEN_PATH).maxAge(Duration.ZERO).build();
    }

    public ResponseCookie oauthState(String value) {
        return builder(OAUTH_STATE, value, OAUTH_STATE_PATH).maxAge(OAUTH_STATE_MAX_AGE).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(SAME_SITE)
                .path(path);
    }
}
