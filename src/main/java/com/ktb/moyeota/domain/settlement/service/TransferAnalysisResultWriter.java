package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.dto.TransferAnalyzedMessage;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferExhaustedEvent;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransferAnalysisResultWriter {

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<Delivery> write(TransferAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        return reflect(request, outcome);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> writeInNewTransaction(
            TransferAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        return reflect(request, outcome);
    }

    private Optional<Delivery> reflect(TransferAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome) {
        Settlement settlement = settlementRepository.findByIdForUpdate(request.settlementId())
                .orElseThrow(() -> new IllegalStateException("정산이 없다: " + request.settlementId()));
        SettlementParticipant charge = settlementParticipantRepository
                .findBySettlementIdAndUserId(request.settlementId(), request.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "청구가 없다: settlementId=" + request.settlementId() + " userId=" + request.userId()));
        if (!charge.isAnalyzingTransfer(request.attempt())) {
            return Optional.empty();
        }
        switch (outcome) {
            case AnalysisOutcome.Read<Integer> read -> charge.recordTransferAmount(read.result());
            case AnalysisOutcome.Unreadable<Integer> unreadable -> charge.failTransfer();
            case AnalysisOutcome.SystemFailure<Integer> failure -> charge.failTransfer();
        }
        if (charge.isTransferAnalysisExhausted()) {
            eventPublisher.publishEvent(
                    new SettlementTransferExhaustedEvent(settlement.getId(), settlement.getCompanion().getId()));
        }
        return Optional.of(new Delivery(List.of(request.userId(), settlement.getPayee().getId()),
                TransferAnalyzedMessage.from(request.settlementId(), request.userId(), charge)));
    }

    public record Delivery(List<Long> recipientIds, TransferAnalyzedMessage message) {
    }
}
