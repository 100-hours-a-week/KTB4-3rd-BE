package com.ktb.moyeota.global.external.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.ai")
public record AiProperties(
        String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout) {

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("moyeota.ai.base-url이 필요합니다.");
        }
    }
}
