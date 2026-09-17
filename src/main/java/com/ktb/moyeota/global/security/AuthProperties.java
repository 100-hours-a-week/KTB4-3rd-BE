package com.ktb.moyeota.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.auth")
public record AuthProperties(Jwt jwt) {

    public AuthProperties {
        if (jwt == null) {
            throw new IllegalStateException("moyeota.auth.jwt 설정이 필요합니다.");
        }
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {

        private static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "moyeota.auth.jwt.secret은 UTF-8 기준 %d바이트 이상이어야 합니다.".formatted(MIN_SECRET_BYTES));
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("moyeota.auth.jwt.issuer가 필요합니다.");
            }
            if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
                throw new IllegalStateException("moyeota.auth.jwt.access-token-ttl은 양수여야 합니다.");
            }
        }
    }
}
