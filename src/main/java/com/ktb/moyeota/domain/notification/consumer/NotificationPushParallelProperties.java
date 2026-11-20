package com.ktb.moyeota.domain.notification.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = NotificationPushParallelProperties.PREFIX)
public record NotificationPushParallelProperties(
        @DefaultValue("24") int concurrency
) {

    public static final String PREFIX = "moyeota.notification.push.kafka.push-parallel";

    public NotificationPushParallelProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("push-parallel.concurrency는 1 이상이어야 합니다");
        }
    }
}
