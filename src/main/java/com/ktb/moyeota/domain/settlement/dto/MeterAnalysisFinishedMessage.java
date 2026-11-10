package com.ktb.moyeota.domain.settlement.dto;

import com.ktb.moyeota.domain.settlement.entity.Settlement;

public record MeterAnalysisFinishedMessage(
        String type,
        Long settlementId,
        String meterAnalysisStatus,
        int remainingMeterAttempts) {

    private static final String TYPE = "METER_ANALYSIS_FINISHED";

    public static MeterAnalysisFinishedMessage from(Settlement settlement) {
        return new MeterAnalysisFinishedMessage(TYPE, settlement.getId(),
                settlement.getMeterAnalysisStatus().name(), settlement.remainingMeterAttempts());
    }
}
