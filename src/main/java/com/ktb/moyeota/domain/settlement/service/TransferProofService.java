package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MyTransfer;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferProofService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;
    private final AccountNoCipher accountNoCipher;

    @Transactional(readOnly = true)
    public MyTransfer findMine(Long userId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(found -> settlementRiderRepository.existsRider(found.getCompanion().getId(), userId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.isPaidTo(userId)) {
            throw new BusinessException(SettlementErrorCode.PAYEE_HAS_NO_TRANSFER);
        }
        return new MyTransfer(settlement.getParticipantCount(), settlement.getStatus(),
                settlementParticipantRepository.findBySettlementIdAndUserId(settlementId, userId)
                        .map(charge -> toCharge(settlement, charge)));
    }

    private MyTransfer.Charge toCharge(Settlement settlement, SettlementParticipant charge) {
        MyTransfer.Payee payee = new MyTransfer.Payee(settlement.getPayee().getName(),
                settlement.getPayeeBankName(), accountNoCipher.decrypt(settlement.getPayeeAccountNo()));
        return new MyTransfer.Charge(charge.getAmount(), charge.getAnalysisStatus(), charge.getTransferredAt(), payee);
    }
}
