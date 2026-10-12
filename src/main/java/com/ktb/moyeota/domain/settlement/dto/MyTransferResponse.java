package com.ktb.moyeota.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public record MyTransferResponse(
        int participantCount,
        String groupSettlementStatus,
        @JsonInclude(NON_NULL) String memberAnalysisStatus,
        @JsonInclude(NON_NULL) Integer amount,
        @JsonInclude(NON_NULL) LocalDateTime transferredAt,
        @JsonInclude(NON_NULL) Payee payee) {

    public record Payee(String name, String bankName, String accountNo) {
    }
}
