package com.ktb.moyeota.domain.carpool.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarpoolRideStartedEvent(
        Long carpoolId,
        LocalDateTime startedAt,
        BigDecimal originLat,
        BigDecimal originLng,
        BigDecimal destLat,
        BigDecimal destLng) {
}
