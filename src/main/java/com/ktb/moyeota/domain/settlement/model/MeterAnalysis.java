package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;

public record MeterAnalysis(
        SettlementStatus status,
        MeterAnalysisStatus meterAnalysisStatus,
        Integer totalAmount,
        LocalDateTime analysisStartedAt,
        int remainingMeterAttempts) {

    public static MeterAnalysis from(Settlement settlement) {
        return new MeterAnalysis(settlement.getStatus(), settlement.getMeterAnalysisStatus(),
                settlement.getTotalAmount(), settlement.getAnalysisStartedAt(), settlement.remainingMeterAttempts());
    }
}
