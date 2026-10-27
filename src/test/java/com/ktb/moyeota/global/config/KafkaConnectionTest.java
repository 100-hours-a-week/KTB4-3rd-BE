package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, KafkaTestContainerConfig.class, KafkaConnectionTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class KafkaConnectionTest {

    private static final String TOPIC = "kafka-connection-test";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<SpotMessage> received;

    @Test
    @DisplayName("문자열로 보낸 JSON 메시지를 리스너가 스네이크 케이스 그대로 객체로 받는다")
    void receivesJsonAsRecord() throws Exception {
        kafkaTemplate.send(TOPIC, "101", "{\"spot_id\":101,\"spot_name\":\"판교역\"}").get(10, TimeUnit.SECONDS);

        assertThat(received.poll(30, TimeUnit.SECONDS)).isEqualTo(new SpotMessage(101L, "판교역"));
    }

    record SpotMessage(Long spotId, String spotName) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic kafkaConnectionTestTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        BlockingQueue<SpotMessage> received() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        SpotListener spotListener(BlockingQueue<SpotMessage> received) {
            return new SpotListener(received);
        }
    }

    static class SpotListener {

        private final BlockingQueue<SpotMessage> received;

        SpotListener(BlockingQueue<SpotMessage> received) {
            this.received = received;
        }

        @KafkaListener(topics = TOPIC, groupId = "kafka-connection-test")
        void listen(SpotMessage message) {
            received.add(message);
        }
    }
}
