package com.ktb.moyeota.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.global.security.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class AccessTokenProviderTest {

    private static final String SECRET = "test-only-moyeota-access-token-secret-0123456789";
    private static final String OTHER_SECRET = "another-service-signing-secret-abcdefghijklmnop";
    private static final String ISSUER = "moyeota";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    @DisplayName("발급한 토큰을 같은 설정의 디코더로 풀면 userId와 수명이 그대로 나온다")
    void issueThenDecode() {
        AccessToken token = providerAt(NOW).issue(42L);

        Jwt decoded = decoderAt(NOW).decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.expiresIn()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 서명 검증에서 거부된다")
    void rejectsTokenSignedWithAnotherKey() {
        AccessToken forged = providerAt(NOW, OTHER_SECRET, ISSUER).issue(42L);

        assertThatThrownBy(() -> decoderAt(NOW).decode(forged.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 거부된다")
    void rejectsExpiredToken() {
        AccessToken token = providerAt(NOW).issue(42L);

        JwtDecoder afterExpiry = decoderAt(NOW.plus(Duration.ofMinutes(32)));

        assertThatThrownBy(() -> afterExpiry.decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("exp");
    }

    @Test
    @DisplayName("만료 직후라도 허용 오차 안이면 아직 유효하다")
    void acceptsTokenWithinClockSkew() {
        AccessToken token = providerAt(NOW).issue(42L);

        JwtDecoder justAfterExpiry = decoderAt(NOW.plus(TTL).plus(Duration.ofSeconds(30)));

        assertThat(justAfterExpiry.decode(token.value()).getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("발급자가 다른 토큰은 거부된다")
    void rejectsTokenFromAnotherIssuer() {
        AccessToken token = providerAt(NOW, SECRET, "someone-else").issue(42L);

        assertThatThrownBy(() -> decoderAt(NOW).decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    private AccessTokenProvider providerAt(Instant now) {
        return providerAt(now, SECRET, ISSUER);
    }

    private AccessTokenProvider providerAt(Instant now, String secret, String issuer) {
        AuthProperties properties = properties(secret, issuer);
        return new AccessTokenProvider(
                jwtConfig.jwtEncoder(jwtConfig.jwtSecretKey(properties)), properties, fixedAt(now));
    }

    private JwtDecoder decoderAt(Instant now) {
        AuthProperties properties = properties(SECRET, ISSUER);
        return jwtConfig.jwtDecoder(jwtConfig.jwtSecretKey(properties), properties, fixedAt(now));
    }

    private AuthProperties properties(String secret, String issuer) {
        return new AuthProperties(
                new AuthProperties.Jwt(secret, issuer, TTL),
                new AuthProperties.Refresh(Duration.ofDays(7), Duration.ofSeconds(10)),
                new AuthProperties.Signup(Duration.ofMinutes(15)),
                new AuthProperties.Cookie(false));
    }

    private Clock fixedAt(Instant now) {
        return Clock.fixed(now, ZoneOffset.UTC);
    }
}
