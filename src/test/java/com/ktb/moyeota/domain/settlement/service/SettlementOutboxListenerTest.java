package com.ktb.moyeota.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.settlement.event.SettlementCompletedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementConfirmRequestedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferDoneEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferExhaustedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferRequestedEvent;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({SettlementOutboxListener.class, OutboxRecorder.class, EventEnvelopeFactory.class, TestClockConfig.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class SettlementOutboxListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("송금 요청 · 송금 확인 어려움 · 정산 완료를 정산 바뀜 토픽에 동행 키로 남긴다")
    void recordsSettlementFacts() {
        eventPublisher.publishEvent(new SettlementTransferRequestedEvent(11L, 101L));
        eventPublisher.publishEvent(new SettlementTransferExhaustedEvent(11L, 101L));
        eventPublisher.publishEvent(new SettlementCompletedEvent(11L, 101L));

        List<OutboxEvent> recorded = outboxEventRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId))
                .toList();
        assertThat(recorded).extracting(OutboxEvent::getTopic, OutboxEvent::getMessageKey, OutboxEvent::getEventType)
                .containsExactly(
                        tuple(SettlementChangedMessages.TOPIC, "101", SettlementChangedMessages.TRANSFER_REQUESTED),
                        tuple(SettlementChangedMessages.TOPIC, "101", SettlementChangedMessages.TRANSFER_EXHAUSTED),
                        tuple(SettlementChangedMessages.TOPIC, "101", SettlementChangedMessages.COMPLETED));
        JsonNode data = jsonMapper.readTree(recorded.get(0).getPayload());
        assertThat(data.get("settlement_id").asLong()).isEqualTo(11L);
        assertThat(data.get("companion_id").asLong()).isEqualTo(101L);
    }

    @Test
    @DisplayName("송금 완료는 송금한 사람과 함께, 방장 확인 요청은 정산 · 동행만 남긴다")
    void recordsTransferDoneAndConfirmRequested() {
        eventPublisher.publishEvent(new SettlementTransferDoneEvent(11L, 101L, 7L));
        eventPublisher.publishEvent(new SettlementConfirmRequestedEvent(11L, 101L));

        List<OutboxEvent> recorded = outboxEventRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId))
                .toList();
        assertThat(recorded).extracting(OutboxEvent::getTopic, OutboxEvent::getMessageKey, OutboxEvent::getEventType)
                .containsExactly(
                        tuple(SettlementChangedMessages.TOPIC, "101", SettlementChangedMessages.TRANSFER_DONE),
                        tuple(SettlementChangedMessages.TOPIC, "101", SettlementChangedMessages.CONFIRM_REQUESTED));
        assertThat(jsonMapper.readTree(recorded.get(0).getPayload()).get("user_id").asLong()).isEqualTo(7L);
        assertThat(jsonMapper.readTree(recorded.get(1).getPayload()).get("user_id").isNull()).isTrue();
    }
}
