package com.ktb.moyeota.domain.settlement.dto;

import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;

public record TransferAnalyzedMessage(
        String type,
        Long settlementId,
        Long userId,
        String name,
        String memberAnalysisStatus,
        boolean transferCompleted,
        int remainingTransferAttempts) {

    private static final String TYPE = "TRANSFER_ANALYZED";

    public static TransferAnalyzedMessage from(Long settlementId, Long userId, SettlementParticipant charge) {
        return new TransferAnalyzedMessage(TYPE, settlementId, userId, charge.getUser().getName(),
                charge.getAnalysisStatus().name(),
                charge.isTransferCompleted(), charge.remainingTransferAttempts());
    }
}
