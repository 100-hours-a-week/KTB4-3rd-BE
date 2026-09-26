package com.ktb.moyeota.domain.taxipot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TaxiPotStatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "^(IN_PROGRESS|COMPLETED)$", message = "INVALID_ENUM")
        String status) {
}
