package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.model.TransferSubmission;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TransferProofSubmissionService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;
    private final ImagePromotionService imagePromotionService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public TransferSubmission submit(Long userId, Long settlementId, String tmpKey) {
        transactionTemplate.executeWithoutResult(status -> {
            findForPayer(settlementId, userId).checkTransferSubmittable();
            findCharge(settlementId, userId).checkTransferSubmittable();
        });
        String transferImageKey = imagePromotionService.promote(UploadScope.user(userId), ImagePurpose.TRANSFER, tmpKey);
        return transactionTemplate.execute(status -> {
            Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                    .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
            settlement.checkTransferSubmittable();
            SettlementParticipant charge = findCharge(settlementId, userId);
            charge.submitTransfer(transferImageKey);
            eventPublisher.publishEvent(new TransferAnalysisRequestedEvent(settlementId, userId, transferImageKey,
                    charge.getTransferImageAnalysisTrialCount(), LocalDateTime.now(clock)));
            return new TransferSubmission(settlement.getStatus(), charge.remainingTransferAttempts());
        });
    }

    private Settlement findForPayer(Long settlementId, Long userId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(found -> settlementRiderRepository.existsRider(found.getCompanion().getId(), userId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.isPaidTo(userId)) {
            throw new BusinessException(SettlementErrorCode.PAYEE_HAS_NO_TRANSFER);
        }
        return settlement;
    }

    private SettlementParticipant findCharge(Long settlementId, Long userId) {
        return settlementParticipantRepository.findBySettlementIdAndUserId(settlementId, userId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.TRANSFER_NOT_FOUND));
    }
}
