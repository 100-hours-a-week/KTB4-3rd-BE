package com.ktb.moyeota.global.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.entity.OutboxStatus;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({OutboxRecorder.class, EventEnvelopeFactory.class, ClockConfig.class})
class OutboxRecorderTest {

    @Autowired
    private OutboxRecorder outboxRecorder;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("토픽 · 키 · 봉투 필드 · data 를 발행 대기 아웃박스로 저장한다")
    void records() {
        MDC.put(TraceId.LOG_KEY, "trace-recorder-1");

        Long id = outboxRecorder.record("matching.request", "2079:5606", "matching.canceled", 1,
                new Canceled(101L)).getId();

        OutboxEvent saved = outboxEventRepository.findById(id).orElseThrow();
        assertThat(saved.getTopic()).isEqualTo("matching.request");
        assertThat(saved.getMessageKey()).isEqualTo("2079:5606");
        assertThat(saved.getEventId()).isNotBlank();
        assertThat(saved.getEventType()).isEqualTo("matching.canceled");
        assertThat(saved.getSchemaVersion()).isEqualTo(1);
        assertThat(saved.getOccurredAt()).isNotNull();
        assertThat(saved.getTraceId()).isEqualTo("trace-recorder-1");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        JsonNode payload = jsonMapper.readTree(saved.getPayload());
        assertThat(payload.get("request_id").asLong()).isEqualTo(101L);
    }

    @Test
    @DisplayName("이벤트마다 다른 event_id 를 갖는다")
    void givesEachEventItsOwnId() {
        OutboxEvent first = outboxRecorder.record("matching.request", "2079:5606", "matching.canceled", 1,
                new Canceled(101L));
        OutboxEvent second = outboxRecorder.record("matching.request", "2079:5606", "matching.canceled", 1,
                new Canceled(101L));

        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("트랜잭션 밖에서는 기록하지 않는다. 도메인 저장과 같은 트랜잭션에서만 쓴다")
    void requiresTransaction() {
        assertThatThrownBy(() -> outboxRecorder.record("matching.request", "2079:5606", "matching.canceled", 1,
                new Canceled(101L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    record Canceled(Long requestId) {
    }
}
