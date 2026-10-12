package com.ktb.moyeota.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

public record MeterAnalysisResponse(
        String groupSettlementStatus,
        String meterAnalysisStatus,
        Integer totalAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime analysisStartedAt) {
}
