package com.ktb.moyeota.domain.notification.message;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

@TestConfiguration(proxyBeanMethods = false)
public class NotificationPushTopicsTestConfig {

    public static final int PARTITIONS = 6;
    public static final int DLT_PARTITIONS = 1;
    public static final String PUSH_DLT = NotificationPushMessages.PUSH_TOPIC + "-dlt";
    public static final String BULK_PUSH_DLT = NotificationPushMessages.BULK_PUSH_TOPIC + "-dlt";

    private static final String RETENTION_MS = String.valueOf(86_400_000L);

    @Bean
    NewTopics notificationPushTopics() {
        return new NewTopics(
                requestTopic(NotificationPushMessages.PUSH_TOPIC),
                requestTopic(NotificationPushMessages.BULK_PUSH_TOPIC),
                TopicBuilder.name(PUSH_DLT).partitions(DLT_PARTITIONS).replicas(1).build(),
                TopicBuilder.name(BULK_PUSH_DLT).partitions(DLT_PARTITIONS).replicas(1).build());
    }

    private static NewTopic requestTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(1)
                .config("retention.ms", RETENTION_MS)
                .build();
    }
}
