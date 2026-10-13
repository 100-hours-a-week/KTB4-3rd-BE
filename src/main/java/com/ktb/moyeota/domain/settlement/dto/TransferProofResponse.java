package com.ktb.moyeota.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public record TransferProofResponse(
        String memberAnalysisStatus,
        @JsonInclude(NON_NULL) String imageUrl,
        @JsonInclude(NON_NULL) LocalDateTime transferredAt) {
}
