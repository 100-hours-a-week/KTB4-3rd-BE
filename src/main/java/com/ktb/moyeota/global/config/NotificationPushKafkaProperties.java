package com.ktb.moyeota.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.notification.push.kafka")
public record NotificationPushKafkaProperties(
        @DefaultValue("2m") Duration republishAfter,
        @DefaultValue("100") int republishBatchSize,
        @DefaultValue("10s") Duration bulkRetryInitialInterval,
        @DefaultValue("6") int bulkConcurrency
) {

    public NotificationPushKafkaProperties {
        if (!republishAfter.isPositive()) {
            throw new IllegalArgumentException("republish-after는 0보다 커야 합니다");
        }
        if (republishBatchSize < 1) {
            throw new IllegalArgumentException("republish-batch-size는 1 이상이어야 합니다");
        }
        if (!bulkRetryInitialInterval.isPositive()) {
            throw new IllegalArgumentException("bulk-retry-initial-interval은 0보다 커야 합니다");
        }
        if (bulkConcurrency < 1) {
            throw new IllegalArgumentException("bulk-concurrency는 1 이상이어야 합니다");
        }
    }
}
