package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;

    @Transactional
    public RequestedSettlement requestManually(Long userId, Long settlementId, int totalAmount) {
        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        List<CompanionParticipant> riders = settlementRiderRepository.findRiders(settlement.getCompanion().getId());
        validateHost(settlement, riders, userId);

        settlement.requestManually(totalAmount);
        chargeRiders(settlement, riders);
        return new RequestedSettlement(settlement.getParticipantCount(), settlement.amountPerPerson(),
                settlement.getStatus(), settlement.getMeterAnalysisStatus());
    }

    private void chargeRiders(Settlement settlement, List<CompanionParticipant> riders) {
        int amountPerPerson = settlement.amountPerPerson();
        settlementParticipantRepository.saveAll(riders.stream()
                .filter(rider -> !settlement.isPaidTo(rider.getUser().getId()))
                .map(rider -> SettlementParticipant.charge(settlement, rider.getUser(), amountPerPerson))
                .toList());
    }

    private static void validateHost(Settlement settlement, List<CompanionParticipant> riders, Long userId) {
        if (settlement.isPaidTo(userId)) {
            return;
        }
        boolean rider = riders.stream().anyMatch(participant -> participant.getUser().getId().equals(userId));
        throw new BusinessException(rider ? TaxiPotErrorCode.HOST_ONLY : SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }
}
