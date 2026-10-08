package com.ktb.moyeota.domain.carpool.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarpoolCreateCommand(
        String originName,
        BigDecimal originLat,
        BigDecimal originLng,
        String destName,
        BigDecimal destLat,
        BigDecimal destLng,
        LocalDateTime departureAt,
        int recruitCount) {
}
