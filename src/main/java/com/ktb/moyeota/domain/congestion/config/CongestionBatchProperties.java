package com.ktb.moyeota.domain.congestion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.congestion.batch")
public record CongestionBatchProperties(@DefaultValue("30s") Duration grace) {

    public CongestionBatchProperties {
        if (grace.isNegative()) {
            throw new IllegalArgumentException("구간 끝 유예는 0 이상이어야 합니다");
        }
    }
}
