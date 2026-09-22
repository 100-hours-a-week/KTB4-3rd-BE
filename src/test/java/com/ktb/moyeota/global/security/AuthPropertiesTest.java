package com.ktb.moyeota.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    private static final String VALID_SECRET = "test-only-moyeota-access-token-secret-0123456789";

    @Test
    @DisplayName("HS256에 못 미치는 짧은 키는 기동 시점에 거부한다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new AuthProperties.Jwt("too-short-secret", "moyeota", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("32바이트 이상이면 통과한다")
    void acceptsSecretOfMinimumLength() {
        assertThatCode(() -> new AuthProperties.Jwt(VALID_SECRET, "moyeota", Duration.ofMinutes(30)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발급자가 비어 있으면 거부한다")
    void rejectsBlankIssuer() {
        assertThatThrownBy(() -> new AuthProperties.Jwt(VALID_SECRET, " ", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("회원가입 세션 수명이 0 이하이면 거부한다")
    void rejectsNonPositiveSignupTtl() {
        assertThatThrownBy(() -> new AuthProperties.Signup(Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signup.ttl");
    }

    @Test
    @DisplayName("토큰 수명이 0 이하이면 거부한다")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new AuthProperties.Jwt(VALID_SECRET, "moyeota", Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-ttl");
    }
}
