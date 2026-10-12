package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;

public record MeterAnalysis(
        SettlementStatus status,
        MeterAnalysisStatus meterAnalysisStatus,
        Integer totalAmount,
        LocalDateTime analysisStartedAt) {
}
