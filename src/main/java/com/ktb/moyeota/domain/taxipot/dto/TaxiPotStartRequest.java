package com.ktb.moyeota.domain.taxipot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxiPotStartRequest(
        @NotBlank
        @Size(max = 100)
        String originName,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        @Digits(integer = 3, fraction = 6)
        BigDecimal originLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        @Digits(integer = 3, fraction = 6)
        BigDecimal originLng,

        @NotBlank
        @Size(max = 100)
        String destName,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        @Digits(integer = 3, fraction = 6)
        BigDecimal destLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        @Digits(integer = 3, fraction = 6)
        BigDecimal destLng,

        @NotNull
        LocalDateTime departureAt) {
}
