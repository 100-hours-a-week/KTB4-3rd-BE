package com.ktb.moyeota.domain.taxipot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

public record TaxiPotDetailResponse(
        Long id,
        Long chatRoomId,
        String status,
        String originName,
        String destName,
        LocalDateTime departureAt,
        int currentCount,
        int capacity,
        Long hostId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long settlementId) {
}
