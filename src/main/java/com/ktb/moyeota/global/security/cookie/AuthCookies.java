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

    private static final String REFRESH_TOKEN_PATH = "/auth";
    private static final String SAME_SITE = "Lax";

    private final AuthProperties authProperties;

    public ResponseCookie refreshToken(String value, Duration maxAge) {
        return builder(value).maxAge(maxAge).build();
    }

    public ResponseCookie expiredRefreshToken() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(REFRESH_TOKEN, value)
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(SAME_SITE)
                .path(REFRESH_TOKEN_PATH);
    }
}
