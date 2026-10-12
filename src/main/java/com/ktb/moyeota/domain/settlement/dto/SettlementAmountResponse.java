package com.ktb.moyeota.domain.settlement.dto;

public record SettlementAmountResponse(
        int participantCount,
        int amountPerPerson,
        String groupSettlementStatus,
        String meterAnalysisStatus) {
}
