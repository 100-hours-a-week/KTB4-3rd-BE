package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.event.SettlementCompletedEvent;
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
    public void onCompleted(SettlementCompletedEvent event) {
        record(event.settlementId(), event.companionId(), SettlementChangedMessages.COMPLETED);
    }

    private void record(Long settlementId, Long companionId, String eventType) {
        outboxRecorder.record(SettlementChangedMessages.TOPIC, SettlementChangedMessages.key(companionId), eventType,
                SettlementChangedMessages.SCHEMA_VERSION, new SettlementChangedData(settlementId, companionId));
    }
}
