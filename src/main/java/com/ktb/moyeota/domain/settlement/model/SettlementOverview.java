package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementOverview(
        Long id,
        SettlementStatus status,
        MeterAnalysisStatus meterAnalysisStatus,
        Integer totalAmount,
        Integer amountPerPerson,
        int participantCount,
        LocalDateTime completedAt,
        List<Payer> payers) {

    public record Payer(
            Long userId,
            String name,
            String profileImageUrl,
            int amount,
            TransferAnalysisStatus analysisStatus,
            LocalDateTime transferredAt,
            boolean hasTransferProof) {
    }
}
