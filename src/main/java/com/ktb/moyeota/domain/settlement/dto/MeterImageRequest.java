package com.ktb.moyeota.domain.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeterImageRequest(
        @NotBlank
        @Size(max = 500)
        String meterImageKey) {
}
