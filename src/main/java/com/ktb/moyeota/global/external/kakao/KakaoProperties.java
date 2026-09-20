package com.ktb.moyeota.global.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.oauth.kakao")
public record KakaoProperties(String clientId, String redirectUri, String authorizeUri) {

    public KakaoProperties {
        requireText(clientId, "client-id");
        requireText(redirectUri, "redirect-uri");
        requireText(authorizeUri, "authorize-uri");
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("moyeota.oauth.kakao.%s가 필요합니다.".formatted(key));
        }
    }
}
