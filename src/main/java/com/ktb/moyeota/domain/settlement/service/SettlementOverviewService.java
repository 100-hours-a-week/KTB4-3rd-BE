package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.SettlementOverview;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementOverviewService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public SettlementOverview find(Long userId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(found -> settlementRiderRepository.existsRider(found.getCompanion().getId(), userId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (!settlement.isPaidTo(userId)) {
            throw new BusinessException(TaxiPotErrorCode.HOST_ONLY);
        }
        return new SettlementOverview(
                settlement.getId(),
                settlement.getStatus(),
                settlement.getMeterAnalysisStatus(),
                settlement.getTotalAmount(),
                settlement.getTotalAmount() == null ? null : settlement.amountPerPerson(),
                settlement.getParticipantCount(),
                settlement.getCompletedAt(),
                settlementParticipantRepository.findAllWithUserBySettlementId(settlementId).stream()
                        .map(this::toPayer)
                        .toList());
    }

    private SettlementOverview.Payer toPayer(SettlementParticipant charge) {
        User payer = charge.getUser();
        return new SettlementOverview.Payer(
                payer.getId(),
                payer.getName(),
                imageUrlResolver.toUrl(payer.getProfileImageUrl()),
                charge.getAmount(),
                charge.getAnalysisStatus(),
                charge.getTransferredAt(),
                charge.getTransferImageUrl() != null);
    }
}
