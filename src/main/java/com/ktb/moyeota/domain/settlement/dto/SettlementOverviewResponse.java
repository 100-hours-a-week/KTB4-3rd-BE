package com.ktb.moyeota.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public record SettlementOverviewResponse(
        Long id,
        String groupSettlementStatus,
        String meterAnalysisStatus,
        Integer totalAmount,
        @JsonInclude(NON_NULL) Integer amountPerPerson,
        int participantCount,
        @JsonInclude(NON_NULL) LocalDateTime completedAt,
        List<Payer> participants) {

    public record Payer(
            Long userId,
            String name,
            String profileImageUrl,
            int amount,
            String memberAnalysisStatus,
            @JsonInclude(NON_NULL) LocalDateTime transferredAt,
            boolean hasTransferProof) {
    }
}
