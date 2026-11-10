package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;

public record RequestedSettlement(
        int participantCount,
        int amountPerPerson,
        SettlementStatus status,
        MeterAnalysisStatus meterAnalysisStatus) {
}
