package com.ktb.moyeota.domain.companionpost.dto;

import com.ktb.moyeota.domain.companion.entity.TransportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POST /companion-posts 요청. departure_at 미래시각 검증, origin/dest 동일 여부,
 * recruit_count 범위(이동수단별)는 필드 간 교차 검증이라 Service 계층에서 처리한다.
 */
public record CompanionPostCreateRequest(

        @NotBlank
        @Size(max = 100)
        String originName,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal originLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal originLng,

        @NotBlank
        @Size(max = 100)
        String destName,

        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal destLat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal destLng,

        @NotNull
        LocalDateTime departureAt,

        @NotNull
        TransportType transportType,

        @NotNull
        Integer recruitCount,

        @Size(max = 500)
        String content
) {
}
