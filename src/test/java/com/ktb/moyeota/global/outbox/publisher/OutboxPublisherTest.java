package com.ktb.moyeota.global.outbox.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.outbox.config.OutboxProperties;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.entity.OutboxStatus;
import com.ktb.moyeota.global.outbox.publisher.OutboxPublisher.PublishResult;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.event.EventHeaders;
import com.ktb.moyeota.global.trace.TraceId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.properties.max.block.ms=5000",
        "moyeota.outbox.ack-timeout=10s",
        "moyeota.outbox.stuck-attempts=3"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
@Import({OutboxPublisher.class, KafkaConfig.class, ClockConfig.class, KafkaTestContainerConfig.class,
        OutboxPublisherTest.ListenerConfig.class})
class OutboxPublisherTest {

    private static final String TOPIC = "outbox-publisher-test";
    private static final String INVALID_TOPIC = "outbox invalid topic";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 18, 0, 0, 512_000_000);
    private static final int STUCK_ATTEMPTS = 3;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> received;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("발행 대기 이벤트를 봉투로 조립해 토픽 · 키 · 이벤트 종류 · 추적 ID 헤더와 함께 보내고 발행 완료로 바꾼다")
    void publishesEnvelope() throws Exception {
        OutboxEvent event = persist(event(TOPIC, "101:7", "trace-outbox-1"));

        PublishResult result = outboxPublisher.publishPending();

        assertThat(result).isEqualTo(new PublishResult(1, 0));
        ConsumerRecord<String, String> record = awaitRecord(event.getEventId());
        assertThat(record.key()).isEqualTo("101:7");
        assertThat(new String(record.headers().lastHeader(EventHeaders.EVENT_TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("settlement.transfer-analysis.requested");
        assertThat(new String(record.headers().lastHeader(TraceId.KAFKA_HEADER).value(), StandardCharsets.UTF_8))
                .isEqualTo("trace-outbox-1");
        JsonNode json = jsonMapper.readTree(record.value());
        assertThat(json.propertyNames()).containsExactly(
                "event_id", "event_type", "schema_version", "occurred_at", "data");
        assertThat(json.get("event_type").asString()).isEqualTo("settlement.transfer-analysis.requested");
        assertThat(json.get("schema_version").asInt()).isEqualTo(1);
        assertThat(OffsetDateTime.parse(json.get("occurred_at").asString()))
                .isAtSameInstantAs(OffsetDateTime.parse("2026-10-29T18:00:00.512+09:00"));
        assertThat(json.get("data").get("settlement_id").asLong()).isEqualTo(101L);
        assertThat(json.get("data").get("user_id").asLong()).isEqualTo(7L);

        OutboxEvent saved = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("한 이벤트의 발행이 실패해도 같은 배치의 다른 이벤트는 발행되고, 실패한 이벤트는 기록만 남고 대기로 남는다")
    void publishesOthersWhenOneFails() throws Exception {
        OutboxEvent failing = persist(event(INVALID_TOPIC, "101:7", null));
        OutboxEvent other = persist(event(TOPIC, "2079:5606", null));

        PublishResult result = outboxPublisher.publishPending();

        assertThat(result).isEqualTo(new PublishResult(1, 1));
        awaitRecord(other.getEventId());
        OutboxEvent failed = outboxEventRepository.findById(failing.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).isNotBlank();
        assertThat(outboxEventRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("실패한 적 있는 키는 그 이벤트만 다시 보내고 같은 키의 뒤 이벤트는 보류한다. 다른 키는 그대로 보낸다")
    void holdsLaterEventsOfFailedKey() throws Exception {
        OutboxEvent failedBefore = event(TOPIC, "2079:5606", null);
        failedBefore.recordFailure("ack 대기 시간 초과");
        persist(failedBefore);
        OutboxEvent sameKey = persist(event(TOPIC, "2079:5606", null));
        OutboxEvent otherKey = persist(event(TOPIC, "2084:5603", null));

        PublishResult result = outboxPublisher.publishPending();

        assertThat(result).isEqualTo(new PublishResult(2, 0));
        awaitRecord(failedBefore.getEventId());
        awaitRecord(otherKey.getEventId());
        assertThat(outboxEventRepository.findById(failedBefore.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(otherKey.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        OutboxEvent held = outboxEventRepository.findById(sameKey.getId()).orElseThrow();
        assertThat(held.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(held.getAttempts()).isZero();
    }

    @Test
    @DisplayName("같은 배치에서 앞 이벤트를 보내지 못하면 같은 키의 뒤 이벤트는 보내지 않고 대기로 둔다. 다른 키는 그대로 보낸다")
    void holdsLaterEventsWhenEarlierSendFailsInSameBatch() throws Exception {
        OutboxEvent oversized = persist(oversizedEvent("8004"));
        OutboxEvent sameKey = persist(event(TOPIC, "8004", null));
        OutboxEvent otherKey = persist(event(TOPIC, "8005", null));

        PublishResult result = outboxPublisher.publishPending();

        assertThat(result).isEqualTo(new PublishResult(1, 1));
        awaitRecord(otherKey.getEventId());
        assertThat(outboxEventRepository.findById(oversized.getId()).orElseThrow().getAttempts()).isEqualTo(1);
        OutboxEvent held = outboxEventRepository.findById(sameKey.getId()).orElseThrow();
        assertThat(held.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(held.getAttempts()).isZero();
    }

    @Test
    @DisplayName("막힌 키의 행이 한 번에 읽는 개수보다 많이 쌓여도 그 뒤 다른 키의 이벤트는 발행한다")
    void publishesOtherKeysBehindHeldRows() throws Exception {
        OutboxEvent stuck = event(INVALID_TOPIC, "8001", null);
        stuck.recordFailure("RecordTooLargeException");
        persist(stuck);
        List<OutboxEvent> held = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            held.add(entityManager.persist(event(INVALID_TOPIC, "8001", null)));
        }
        OutboxEvent other = persist(event(TOPIC, "8002", null));

        PublishResult result = outboxPublisher.publishPending();

        assertThat(result).isEqualTo(new PublishResult(1, 1));
        awaitRecord(other.getEventId());
        assertThat(outboxEventRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(stuck.getId()).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(outboxEventRepository.findAllById(held.stream().map(OutboxEvent::getId).toList()))
                .allSatisfy(each -> {
                    assertThat(each.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(each.getAttempts()).isZero();
                });
    }

    @Test
    @DisplayName("같은 이벤트가 정한 횟수만큼 실패하면 막힘 지표를 한 번만 올린다")
    void countsStuckOnceWhenAttemptsReachLimit() {
        OutboxEvent failing = event(INVALID_TOPIC, "8003", null);
        for (int i = 1; i < STUCK_ATTEMPTS; i++) {
            failing.recordFailure("RecordTooLargeException");
        }
        persist(failing);

        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        assertThat(outboxEventRepository.findById(failing.getId()).orElseThrow().getAttempts())
                .isEqualTo(STUCK_ATTEMPTS + 1);
        assertThat(meterRegistry.counter(OutboxPublisher.STUCK_METRIC, "topic", INVALID_TOPIC).count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("발행 대기 이벤트가 없으면 아무것도 보내지 않는다")
    void doesNothingWhenEmpty() {
        assertThat(outboxPublisher.publishPending()).isEqualTo(new PublishResult(0, 0));
    }

    private OutboxEvent persist(OutboxEvent event) {
        return entityManager.persistAndFlush(event);
    }

    private OutboxEvent event(String topic, String key, String traceId) {
        return OutboxEvent.create(
                topic,
                key,
                UUID.randomUUID().toString(),
                "settlement.transfer-analysis.requested",
                1,
                OCCURRED_AT,
                "{\"settlement_id\":101,\"user_id\":7}",
                traceId,
                OCCURRED_AT);
    }

    private OutboxEvent oversizedEvent(String key) {
        return OutboxEvent.create(
                TOPIC,
                key,
                UUID.randomUUID().toString(),
                "settlement.transfer-analysis.requested",
                1,
                OCCURRED_AT,
                "{\"settlement_id\":101,\"pad\":\"" + "x".repeat(1_100_000) + "\"}",
                null,
                OCCURRED_AT);
    }

    private ConsumerRecord<String, String> awaitRecord(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = received.poll(1, TimeUnit.SECONDS);
            if (record != null && jsonMapper.readTree(record.value()).get("event_id").asString().equals(eventId)) {
                return record;
            }
        }
        throw new AssertionError("Kafka 에서 event_id=" + eventId + " 메시지를 받지 못했습니다");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxProperties.class)
    static class ListenerConfig {

        @Bean
        NewTopic outboxPublisherTestTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        BlockingQueue<ConsumerRecord<String, String>> received() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        OutboxTestListener outboxTestListener(BlockingQueue<ConsumerRecord<String, String>> received) {
            return new OutboxTestListener(received);
        }
    }

    static class OutboxTestListener {

        private final BlockingQueue<ConsumerRecord<String, String>> received;

        OutboxTestListener(BlockingQueue<ConsumerRecord<String, String>> received) {
            this.received = received;
        }

        @KafkaListener(topics = TOPIC, groupId = "outbox-publisher-test")
        void listen(ConsumerRecord<String, String> record) {
            received.add(record);
        }
    }
}
