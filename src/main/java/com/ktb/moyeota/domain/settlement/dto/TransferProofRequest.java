package com.ktb.moyeota.domain.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransferProofRequest(
        @NotBlank
        @Size(max = 500)
        String transferImageKey) {
}
