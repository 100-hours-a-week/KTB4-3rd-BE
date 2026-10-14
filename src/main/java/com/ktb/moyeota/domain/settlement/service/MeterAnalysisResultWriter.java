package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.dto.MeterAnalysisFinishedMessage;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MeterAnalysisResultWriter {

    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;

    @Transactional
    public Optional<Delivery> write(MeterAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        return reflect(request, outcome);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> writeInNewTransaction(
            MeterAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        return reflect(request, outcome);
    }

    private Optional<Delivery> reflect(MeterAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        Settlement settlement = settlementRepository.findByIdForUpdate(request.settlementId())
                .orElseThrow(() -> new IllegalStateException("정산이 없다: " + request.settlementId()));
        if (!settlement.isAnalyzingMeter(request.startedAt())) {
            return Optional.empty();
        }
        switch (outcome) {
            case AnalysisOutcome.Read<Integer> read -> settlementService.requestByMeter(settlement, read.result());
            case AnalysisOutcome.Unreadable<Integer> unreadable -> settlement.failMeter();
            case AnalysisOutcome.SystemFailure<Integer> failure -> settlement.failMeterBySystem();
        }
        return Optional.of(new Delivery(settlement.getPayee().getId(), MeterAnalysisFinishedMessage.from(settlement)));
    }

    public record Delivery(Long hostId, MeterAnalysisFinishedMessage message) {
    }
}
