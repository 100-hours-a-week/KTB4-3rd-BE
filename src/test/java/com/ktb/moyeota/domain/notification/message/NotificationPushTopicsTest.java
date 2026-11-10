package com.ktb.moyeota.domain.notification.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.kafka.core.KafkaAdmin;

@SpringBootTest(classes = {KafkaTestContainerConfig.class, NotificationPushTopicsTestConfig.class})
@ImportAutoConfiguration({KafkaAutoConfiguration.class, ServiceConnectionAutoConfiguration.class})
class NotificationPushTopicsTest {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    @DisplayName("개별 · 대량 요청 토픽은 파티션 6개, 보존 1일로 뜬다")
    void requestTopics() throws Exception {
        Map<String, TopicDescription> topics = kafkaAdmin.describeTopics(
                NotificationPushMessages.PUSH_TOPIC, NotificationPushMessages.BULK_PUSH_TOPIC);

        assertThat(topics.values()).allSatisfy(topic ->
                assertThat(topic.partitions()).hasSize(NotificationPushTopicsTestConfig.PARTITIONS));
        assertThat(retentionOf(NotificationPushMessages.PUSH_TOPIC)).isEqualTo("86400000");
        assertThat(retentionOf(NotificationPushMessages.BULK_PUSH_TOPIC)).isEqualTo("86400000");
    }

    @Test
    @DisplayName("두 요청 토픽의 -dlt 토픽이 함께 뜬다")
    void deadLetterTopics() {
        Map<String, TopicDescription> topics = kafkaAdmin.describeTopics(
                NotificationPushTopicsTestConfig.PUSH_DLT, NotificationPushTopicsTestConfig.BULK_PUSH_DLT);

        assertThat(topics).containsOnlyKeys(
                NotificationPushTopicsTestConfig.PUSH_DLT, NotificationPushTopicsTestConfig.BULK_PUSH_DLT);
    }

    private String retentionOf(String topic) throws Exception {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Config config = admin.describeConfigs(List.of(resource)).all().get().get(resource);
            return config.get("retention.ms").value();
        }
    }
}
