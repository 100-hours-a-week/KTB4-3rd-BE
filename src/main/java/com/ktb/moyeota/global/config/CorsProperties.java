package com.ktb.moyeota.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("moyeota.cors.allowed-origins가 최소 하나 필요합니다.");
        }
    }
}
