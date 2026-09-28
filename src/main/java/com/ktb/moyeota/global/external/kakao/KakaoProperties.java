package com.ktb.moyeota.global.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.oauth.kakao")
public record KakaoProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizeUri,
        String tokenUri,
        String userInfoUri) {

    public KakaoProperties {
        requireText(clientId, "client-id");
        requireText(clientSecret, "client-secret");
        requireText(redirectUri, "redirect-uri");
        requireText(authorizeUri, "authorize-uri");
        requireText(tokenUri, "token-uri");
        requireText(userInfoUri, "user-info-uri");
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("moyeota.oauth.kakao.%s가 필요합니다.".formatted(key));
        }
    }
}
