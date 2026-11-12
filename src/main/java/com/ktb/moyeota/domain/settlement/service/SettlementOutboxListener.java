package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.event.SettlementCompletedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementConfirmRequestedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferDoneEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferExhaustedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferRequestedEvent;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class SettlementOutboxListener {

    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onTransferRequested(SettlementTransferRequestedEvent event) {
        record(event.settlementId(), event.companionId(), SettlementChangedMessages.TRANSFER_REQUESTED);
    }

    @EventListener
    public void onTransferExhausted(SettlementTransferExhaustedEvent event) {
        record(event.settlementId(), event.companionId(), SettlementChangedMessages.TRANSFER_EXHAUSTED);
    }

    @EventListener
    public void onTransferDone(SettlementTransferDoneEvent event) {
        record(SettlementChangedMessages.TRANSFER_DONE,
                new SettlementChangedData(event.settlementId(), event.companionId(), event.userId()));
    }

    @EventListener
    public void onConfirmRequested(SettlementConfirmRequestedEvent event) {
        record(event.settlementId(), event.companionId(), SettlementChangedMessages.CONFIRM_REQUESTED);
    }

    @EventListener
    public void onCompleted(SettlementCompletedEvent event) {
        record(event.settlementId(), event.companionId(), SettlementChangedMessages.COMPLETED);
    }

    private void record(Long settlementId, Long companionId, String eventType) {
        record(eventType, new SettlementChangedData(settlementId, companionId));
    }

    private void record(String eventType, SettlementChangedData data) {
        outboxRecorder.record(SettlementChangedMessages.TOPIC, SettlementChangedMessages.key(data.companionId()),
                eventType, SettlementChangedMessages.SCHEMA_VERSION, data);
    }
}
