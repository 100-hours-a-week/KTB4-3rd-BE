package com.ktb.moyeota.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("moyeota.cors.allowed-origins가 최소 하나 필요합니다.");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
