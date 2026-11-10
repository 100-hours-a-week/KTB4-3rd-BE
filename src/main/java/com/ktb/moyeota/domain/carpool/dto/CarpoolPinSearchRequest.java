package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.global.common.Viewport;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.BindParam;

public record CarpoolPinSearchRequest(

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
        BigDecimal neLng
) {

    public Viewport toViewport() {
        return new Viewport(swLat, swLng, neLat, neLng);
    }
}
