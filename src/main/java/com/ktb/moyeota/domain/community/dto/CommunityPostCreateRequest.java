package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CommunityPostCreateRequest(

        @NotBlank
        @Size(max = 30)
        String title,

        @NotBlank
        @Size(max = 500)
        String content,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal lng
) {
}
