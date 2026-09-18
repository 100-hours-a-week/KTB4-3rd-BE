package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** GET /map-pins 요청. 뷰포트 남서/북동 좌표, 전부 필수. */
public record MapPinSearchRequest(

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
        BigDecimal neLng
) {
}
