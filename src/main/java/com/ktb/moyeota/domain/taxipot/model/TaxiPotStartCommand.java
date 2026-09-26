package com.ktb.moyeota.domain.taxipot.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxiPotStartCommand(
        String originName,
        BigDecimal originLat,
        BigDecimal originLng,
        String destName,
        BigDecimal destLat,
        BigDecimal destLng,
        LocalDateTime departureAt) {
}
