package com.ktb.moyeota.domain.congestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.congestion.status")
public record CongestionStatusProperties(
        @DefaultValue("3") int accidentReporterThreshold,
        @DefaultValue("3") int delayReporterThreshold) {

    public CongestionStatusProperties {
        if (accidentReporterThreshold < 1 || delayReporterThreshold < 1) {
            throw new IllegalArgumentException("사고 · 지연 기준 제보자 수는 1 이상이어야 합니다");
        }
    }
}
