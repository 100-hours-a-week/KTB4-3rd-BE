package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record NearbyPostSearchRequest(

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal lng,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal swLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal swLng,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal neLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal neLng,

        String cursor
) {
}
