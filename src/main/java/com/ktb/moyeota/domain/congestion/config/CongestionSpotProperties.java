package com.ktb.moyeota.domain.congestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.congestion.spot")
public record CongestionSpotProperties(@DefaultValue("500") int radiusMeters) {

    public CongestionSpotProperties {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("스팟 반경은 0보다 커야 합니다");
        }
    }
}
