package com.ktb.moyeota.domain.notification.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = NotificationBulkPushParallelProperties.PREFIX)
public record NotificationBulkPushParallelProperties(
        @DefaultValue("32") int concurrency,
        @DefaultValue("1") int batchSize
) {

    public static final String PREFIX = "moyeota.notification.push.kafka.bulk-parallel";

    public NotificationBulkPushParallelProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("bulk-parallel.concurrency는 1 이상이어야 합니다");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("bulk-parallel.batch-size는 1 이상이어야 합니다");
        }
    }
}
