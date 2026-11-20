package com.ktb.moyeota.domain.notification.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = NotificationPushConsumerProperties.PREFIX)
public record NotificationPushConsumerProperties(
        @DefaultValue("record") Type type
) {

    public static final String PREFIX = "moyeota.notification.push.kafka.push-consumer";
    public static final String TYPE = "type";

    public enum Type {
        RECORD,
        PARALLEL
    }
}
