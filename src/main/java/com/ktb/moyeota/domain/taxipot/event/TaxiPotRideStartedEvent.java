package com.ktb.moyeota.domain.taxipot.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxiPotRideStartedEvent(
        Long taxiPotId,
        LocalDateTime startedAt,
        BigDecimal originLat,
        BigDecimal originLng,
        BigDecimal destLat,
        BigDecimal destLng) {
}
