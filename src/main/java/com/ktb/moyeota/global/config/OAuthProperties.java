package com.ktb.moyeota.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.oauth")
public record OAuthProperties(String frontCallbackUri) {

    public OAuthProperties {
        if (frontCallbackUri == null || frontCallbackUri.isBlank()) {
            throw new IllegalStateException("moyeota.oauth.front-callback-uri가 필요합니다.");
        }
    }
}
