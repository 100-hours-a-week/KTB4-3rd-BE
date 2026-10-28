package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
        classes = {KafkaConfig.class, KafkaTestContainerConfig.class, KafkaDeadLetterTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class KafkaDeadLetterTest {

    private static final String TOPIC = "kafka-dead-letter-test";
    private static final String DLT = TOPIC + "-dlt";
    private static final long FAILING_COMPANION_ID = 999L;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Autowired
    private Map<String, AtomicInteger> attempts;

    @Test
    @DisplayName("JSON 이 깨진 메시지는 리스너를 부르지 않고 바로 DLT 로 보낸다")
    void sendsBrokenJsonToDeadLetter() throws Exception {
        String broken = "{\"event_id\":\"broken-json\",\"event_type\":";

        send(broken);

        assertThat(awaitDeadLetter("broken-json").value()).isEqualTo(broken);
    }

    @Test
    @DisplayName("봉투가 빠진 메시지는 잘못된 이벤트로 보고 바로 DLT 로 보낸다")
    void sendsMissingEnvelopeToDeadLetter() throws Exception {
        String flat = "{\"event_id\":\"missing-data\",\"companion_id\":55}";

        send(flat);

        assertThat(awaitDeadLetter("missing-data").value()).isEqualTo(flat);
        assertThat(attempts).doesNotContainKey("missing-data");
    }

    @Test
    @DisplayName("리스너가 읽지 못하는 버전은 다시 시도하지 않고 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetterWithoutRetry() throws Exception {
        send(envelope("version-2", 2, 55L));

        awaitDeadLetter("version-2");
        assertThat(attempts.get("version-2")).hasValue(1);
    }

    @Test
    @DisplayName("처리 중 실패는 기본 재시도를 다 쓴 뒤 DLT 로 보낸다")
    void sendsFailedProcessingToDeadLetterAfterRetries() throws Exception {
        send(envelope("keeps-failing", 1, FAILING_COMPANION_ID));

        awaitDeadLetter("keeps-failing");
        assertThat(attempts.get("keeps-failing")).hasValue(10);
    }

    private void send(String value) throws Exception {
        kafkaTemplate.send(TOPIC, "55", value).get(10, TimeUnit.SECONDS);
    }

    private String envelope(String eventId, int schemaVersion, long companionId) {
        return """
                {"event_id":"%s","event_type":"ride.ended","schema_version":%d,
                 "occurred_at":"2026-10-28T18:42:10+09:00","data":{"companion_id":%d}}
                """.formatted(eventId, schemaVersion, companionId);
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String marker) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecord<String, String> record = deadLetters.poll(1, TimeUnit.SECONDS);
            if (record != null && record.value().contains(marker)) {
                return record;
            }
            if (record != null) {
                deadLetters.add(record);
            }
        }
        throw new AssertionError("DLT 에 도착하지 않았다: " + marker);
    }

    record RideEnded(Long companionId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic kafkaDeadLetterTestTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic kafkaDeadLetterTestDlt() {
            return new NewTopic(DLT, 1, (short) 1);
        }

        @Bean
        BlockingQueue<ConsumerRecord<String, String>> deadLetters() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        Map<String, AtomicInteger> attempts() {
            return new ConcurrentHashMap<>();
        }

        @Bean
        RideEndedListener rideEndedListener(Map<String, AtomicInteger> attempts) {
            return new RideEndedListener(attempts);
        }

        @Bean
        DeadLetterListener deadLetterListener(BlockingQueue<ConsumerRecord<String, String>> deadLetters) {
            return new DeadLetterListener(deadLetters);
        }
    }

    static class RideEndedListener {

        private final Map<String, AtomicInteger> attempts;

        RideEndedListener(Map<String, AtomicInteger> attempts) {
            this.attempts = attempts;
        }

        @KafkaListener(topics = TOPIC, groupId = "kafka-dead-letter-test")
        void listen(EventEnvelope<RideEnded> event) {
            attempts.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
            event.requireSchemaVersion(1);
            if (event.data().companionId() == FAILING_COMPANION_ID) {
                throw new IllegalStateException("처리 실패");
            }
        }
    }

    static class DeadLetterListener {

        private final BlockingQueue<ConsumerRecord<String, String>> deadLetters;

        DeadLetterListener(BlockingQueue<ConsumerRecord<String, String>> deadLetters) {
            this.deadLetters = deadLetters;
        }

        @KafkaListener(topics = DLT, groupId = "kafka-dead-letter-test-dlt")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
