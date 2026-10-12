package com.ktb.moyeota.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;


@ConfigurationProperties(prefix = "moyeota.notification.push")
public record NotificationPushProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("10s") Duration sweepDelay,
        @DefaultValue("50") int batchSize,
        @DefaultValue("60s") Duration lease,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue Fcm fcm,
        @DefaultValue Executor executor
) {

    public NotificationPushProperties {
        if (lease.compareTo(fcm.connectTimeout().plus(fcm.readTimeout())) <= 0) {
            throw new IllegalArgumentException("lease는 FCM 타임아웃(connect + read)보다 길어야 합니다");
        }
    }

    public record Fcm(
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout
    ) {
    }

    public record Executor(
            @DefaultValue("2") int corePoolSize,
            @DefaultValue("4") int maxPoolSize,
            @DefaultValue("100") int queueCapacity
    ) {
    }
}
