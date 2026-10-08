package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CarpoolStatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "^(IN_PROGRESS|COMPLETED)$", message = "INVALID_ENUM")
        String status) {

    public CompanionStatus toStatus() {
        return CompanionStatus.valueOf(status);
    }
}
