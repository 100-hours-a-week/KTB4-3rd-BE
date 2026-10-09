package com.ktb.moyeota.domain.carpool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRequestSendRequest(
        @NotBlank
        @Size(max = 200)
        String content) {
}
