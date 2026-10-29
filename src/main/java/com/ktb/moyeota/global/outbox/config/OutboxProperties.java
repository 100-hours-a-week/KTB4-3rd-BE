package com.ktb.moyeota.global.outbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.outbox")
public record OutboxProperties(
        @DefaultValue("1s") Duration publishDelay,
        @DefaultValue("100") int batchSize,
        @DefaultValue("30s") Duration ackTimeout
) {

    public OutboxProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batch-size는 1 이상이어야 합니다");
        }
    }
}
