package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinRequestHandleRequest(
        @NotBlank
        @Pattern(regexp = "^(ACCEPTED|REJECTED)$", message = "INVALID_ENUM")
        String status) {

    public CompanionRequestStatus toStatus() {
        return CompanionRequestStatus.valueOf(status);
    }
}
