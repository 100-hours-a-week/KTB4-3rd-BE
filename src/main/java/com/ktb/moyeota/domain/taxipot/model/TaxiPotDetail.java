package com.ktb.moyeota.domain.taxipot.model;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;

public record TaxiPotDetail(
        Long id,
        CompanionStatus status,
        String originName,
        String destName,
        LocalDateTime departureAt,
        int currentCount,
        int capacity,
        Long hostId) {
}
