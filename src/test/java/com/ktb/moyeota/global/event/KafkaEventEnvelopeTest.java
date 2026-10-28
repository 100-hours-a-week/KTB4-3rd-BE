package com.ktb.moyeota.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import java.time.OffsetDateTime;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = {KafkaConfig.class, KafkaTestContainerConfig.class, KafkaEventEnvelopeTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class KafkaEventEnvelopeTest {

    private static final String TOPIC = "kafka-event-envelope-test";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<EventEnvelope<RideEnded>> received;

    @Test
    @DisplayName("봉투로 보낸 메시지를 리스너가 내용 타입까지 살린 봉투로 받는다")
    void receivesEnvelope() throws Exception {
        EventEnvelope<RideEnded> sent = new EventEnvelope<>(
                "0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90", "ride.ended", 1,
                OffsetDateTime.parse("2026-10-28T18:42:10+09:00"), new RideEnded(55L));

        kafkaTemplate.send(TOPIC, "55", jsonMapper.writeValueAsString(sent)).get(10, TimeUnit.SECONDS);

        EventEnvelope<RideEnded> envelope = received.poll(30, TimeUnit.SECONDS);
        assertThat(envelope).isNotNull();
        assertThat(envelope.eventId()).isEqualTo(sent.eventId());
        assertThat(envelope.eventType()).isEqualTo("ride.ended");
        assertThat(envelope.occurredAt()).isAtSameInstantAs(sent.occurredAt());
        assertThat(envelope.data()).isEqualTo(new RideEnded(55L));
    }

    record RideEnded(Long companionId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic kafkaEventEnvelopeTestTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        BlockingQueue<EventEnvelope<RideEnded>> received() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        RideEndedListener rideEndedListener(BlockingQueue<EventEnvelope<RideEnded>> received) {
            return new RideEndedListener(received);
        }
    }

    static class RideEndedListener {

        private final BlockingQueue<EventEnvelope<RideEnded>> received;

        RideEndedListener(BlockingQueue<EventEnvelope<RideEnded>> received) {
            this.received = received;
        }

        @KafkaListener(topics = TOPIC, groupId = "kafka-event-envelope-test")
        void listen(EventEnvelope<RideEnded> event) {
            received.add(event);
        }
    }
}
