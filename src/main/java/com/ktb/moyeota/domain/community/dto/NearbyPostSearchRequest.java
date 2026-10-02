package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.BindParam;

public record NearbyPostSearchRequest(

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal lng,

        @BindParam("sw_lat")
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal swLat,

        @BindParam("sw_lng")
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal swLng,

        @BindParam("ne_lat")
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal neLat,

        @BindParam("ne_lng")
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal neLng,

        String cursor
) {
}
