package com.ktb.moyeota.global.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class OutboxEventTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 16, 0, 0, 512_000_000);
    private static final LocalDateTime NOW = OCCURRED_AT.plusNanos(1_000_000);
    private static final String TOPIC = "settlement.image-analysis.request";
    private static final String PAYLOAD = "{\"settlement_id\":101,\"user_id\":7,\"image_key\":\"transfer/101/7.jpg\"}";

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("아웃박스는 발행 대기 상태로 토픽 · 키 · 봉투 필드 · data 와 함께 저장된다")
    void createsPending() {
        Long id = entityManager.persistAndFlush(event("0e9f6c1a-0000-0000-0000-000000000001", "101:7")).getId();

        entityManager.clear();
        OutboxEvent saved = entityManager.find(OutboxEvent.class, id);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getTopic()).isEqualTo(TOPIC);
        assertThat(saved.getMessageKey()).isEqualTo("101:7");
        assertThat(saved.getEventId()).isEqualTo("0e9f6c1a-0000-0000-0000-000000000001");
        assertThat(saved.getEventType()).isEqualTo("settlement.transfer-analysis.requested");
        assertThat(saved.getSchemaVersion()).isEqualTo(1);
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.getPayload()).isEqualTo(PAYLOAD);
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("키가 없는 이벤트도 저장된다")
    void allowsNullKey() {
        Long id = entityManager.persistAndFlush(event("0e9f6c1a-0000-0000-0000-000000000002", null)).getId();

        entityManager.clear();
        assertThat(entityManager.find(OutboxEvent.class, id).getMessageKey()).isNull();
    }

    @Test
    @DisplayName("같은 event_id 는 두 번 저장할 수 없다")
    void rejectsDuplicateEventId() {
        entityManager.persistAndFlush(event("0e9f6c1a-0000-0000-0000-000000000003", "101:7"));

        assertThatThrownBy(() -> entityManager.persistAndFlush(event("0e9f6c1a-0000-0000-0000-000000000003", "101:7")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("발행에 실패하면 시도 횟수와 오류만 남고 발행 대기 상태로 남는다")
    void recordsFailureAndStaysPending() {
        OutboxEvent event = event("0e9f6c1a-0000-0000-0000-000000000004", "101:7");

        event.recordFailure("브로커 응답 시간 초과");
        event.recordFailure("브로커 연결 실패");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("브로커 연결 실패");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("오류 내용은 255자까지만 남긴다")
    void truncatesLastError() {
        OutboxEvent event = event("0e9f6c1a-0000-0000-0000-000000000005", "101:7");

        event.recordFailure("x".repeat(300));

        assertThat(event.getLastError()).hasSize(255);
    }

    @Test
    @DisplayName("발행에 성공하면 발행 완료 상태와 발행 시각이 남는다")
    void marksPublished() {
        OutboxEvent event = event("0e9f6c1a-0000-0000-0000-000000000006", "101:7");
        event.recordFailure("브로커 응답 시간 초과");

        event.markPublished(NOW.plusSeconds(5));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 발행된 이벤트에는 결과를 다시 기록할 수 없다")
    void rejectsResultAfterPublished() {
        OutboxEvent event = event("0e9f6c1a-0000-0000-0000-000000000007", "101:7");
        event.markPublished(NOW);

        assertThatThrownBy(() -> event.markPublished(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.recordFailure("브로커 연결 실패"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("토픽 · event_id · event_type · payload 가 비어 있거나 schema_version 이 1 미만이면 만들 수 없다")
    void rejectsMissingFields() {
        assertThatThrownBy(() -> OutboxEvent.create(
                " ", "101:7", "id", "type", 1, OCCURRED_AT, PAYLOAD, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.create(
                TOPIC, "101:7", null, "type", 1, OCCURRED_AT, PAYLOAD, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.create(
                TOPIC, "101:7", "id", "", 1, OCCURRED_AT, PAYLOAD, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.create(
                TOPIC, "101:7", "id", "type", 1, OCCURRED_AT, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.create(
                TOPIC, "101:7", "id", "type", 0, OCCURRED_AT, PAYLOAD, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OutboxEvent event(String eventId, String messageKey) {
        return OutboxEvent.create(
                TOPIC,
                messageKey,
                eventId,
                "settlement.transfer-analysis.requested",
                1,
                OCCURRED_AT,
                PAYLOAD,
                "trace-1",
                NOW);
    }
}
