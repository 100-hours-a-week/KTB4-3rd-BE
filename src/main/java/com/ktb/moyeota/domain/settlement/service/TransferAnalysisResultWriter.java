package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.dto.TransferAnalyzedMessage;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.event.SettlementConfirmRequestedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferDoneEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferExhaustedEvent;
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
    public Optional<Delivery> write(Long settlementId, Long userId, int attempt, AnalysisOutcome<Integer> outcome) {
        return reflect(settlementId, userId, attempt, outcome);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> writeInNewTransaction(
            Long settlementId, Long userId, int attempt, AnalysisOutcome<Integer> outcome) {
        return reflect(settlementId, userId, attempt, outcome);
    }

    private Optional<Delivery> reflect(Long settlementId, Long userId, int attempt, AnalysisOutcome<Integer> outcome) {
        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new IllegalStateException("정산이 없다: " + settlementId));
        SettlementParticipant charge = settlementParticipantRepository
                .findBySettlementIdAndUserId(settlementId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "청구가 없다: settlementId=" + settlementId + " userId=" + userId));
        if (!charge.isAnalyzingTransfer(attempt)) {
            return Optional.empty();
        }
        switch (outcome) {
            case AnalysisOutcome.Read<Integer> read -> charge.recordTransferAmount(read.result());
            case AnalysisOutcome.Unreadable<Integer> unreadable -> charge.failTransfer();
            case AnalysisOutcome.SystemFailure<Integer> failure -> charge.failTransfer();
        }
        if (charge.isTransferCompleted() && settlement.getStatus() == SettlementStatus.REQUESTED) {
            publishTransferDone(settlement, userId);
        }
        if (charge.isTransferAnalysisExhausted()) {
            eventPublisher.publishEvent(
                    new SettlementTransferExhaustedEvent(settlement.getId(), settlement.getCompanion().getId()));
        }
        return Optional.of(new Delivery(List.of(userId, settlement.getPayee().getId()),
                TransferAnalyzedMessage.from(settlementId, userId, charge)));
    }

    private void publishTransferDone(Settlement settlement, Long userId) {
        Long companionId = settlement.getCompanion().getId();
        eventPublisher.publishEvent(new SettlementTransferDoneEvent(settlement.getId(), companionId, userId));
        boolean allDone = settlementParticipantRepository.findAllBySettlementId(settlement.getId()).stream()
                .allMatch(SettlementParticipant::isTransferCompleted);
        if (allDone) {
            eventPublisher.publishEvent(new SettlementConfirmRequestedEvent(settlement.getId(), companionId));
        }
    }

    public record Delivery(List<Long> recipientIds, TransferAnalyzedMessage message) {
    }
}
