package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.message.ImageAnalysisMessages;
import com.ktb.moyeota.domain.settlement.message.TransferAnalysisRequestData;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.settlement.meter-analysis", name = "mode", havingValue = "kafka")
public class TransferAnalysisOutboxListener {

    private final ImageReadUrlIssuer imageReadUrlIssuer;
    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onRequested(TransferAnalysisRequestedEvent event) {
        TransferAnalysisRequestData data = new TransferAnalysisRequestData(
                event.settlementId(),
                event.userId(),
                event.attempt(),
                imageReadUrlIssuer.issue(event.transferImageKey()));
        outboxRecorder.record(
                ImageAnalysisMessages.REQUEST_TOPIC,
                ImageAnalysisMessages.key(event.settlementId(), event.userId()),
                ImageAnalysisMessages.TRANSFER_REQUESTED,
                ImageAnalysisMessages.TRANSFER_SCHEMA_VERSION,
                data);
    }
}
