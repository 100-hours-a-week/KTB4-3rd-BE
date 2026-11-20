package com.ktb.moyeota.domain.notification.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = NotificationBulkPushConsumerProperties.PREFIX)
public record NotificationBulkPushConsumerProperties(
        @DefaultValue("record") Type type,
        @DefaultValue("0") int batchSendThreads
) {

    public static final String PREFIX = "moyeota.notification.push.kafka.bulk-consumer";
    public static final String TYPE = "type";

    public NotificationBulkPushConsumerProperties {
        if (batchSendThreads < 0) {
            throw new IllegalArgumentException("bulk-consumer.batch-send-threads는 0 이상이어야 합니다");
        }
    }

    public enum Type {
        RECORD,
        BATCH
    }
}
