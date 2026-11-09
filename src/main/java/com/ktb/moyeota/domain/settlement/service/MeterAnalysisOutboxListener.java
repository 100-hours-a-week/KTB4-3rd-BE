package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.message.ImageAnalysisMessages;
import com.ktb.moyeota.domain.settlement.message.MeterAnalysisRequestData;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.settlement.meter-analysis", name = "mode", havingValue = "kafka")
public class MeterAnalysisOutboxListener {

    private final SettlementRepository settlementRepository;
    private final ImageReadUrlIssuer imageReadUrlIssuer;
    private final OutboxRecorder outboxRecorder;
    private final Clock clock;

    @EventListener
    public void onRequested(MeterAnalysisRequestedEvent event) {
        Settlement settlement = settlementRepository.findById(event.settlementId())
                .orElseThrow(() -> new IllegalStateException("정산이 없다: " + event.settlementId()));
        MeterAnalysisRequestData data = new MeterAnalysisRequestData(
                settlement.getId(),
                event.startedAt().atZone(clock.getZone()).toOffsetDateTime(),
                imageReadUrlIssuer.issue(event.meterImageKey()));
        outboxRecorder.record(
                ImageAnalysisMessages.REQUEST_TOPIC,
                ImageAnalysisMessages.key(settlement.getId(), settlement.getPayee().getId()),
                ImageAnalysisMessages.METER_REQUESTED,
                ImageAnalysisMessages.METER_SCHEMA_VERSION,
                data);
    }
}
