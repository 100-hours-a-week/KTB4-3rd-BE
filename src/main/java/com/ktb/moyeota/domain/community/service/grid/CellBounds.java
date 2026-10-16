package com.ktb.moyeota.domain.community.service.grid;

import java.math.BigDecimal;

public record CellBounds(BigDecimal minLat, BigDecimal minLng, BigDecimal maxLat, BigDecimal maxLng) {

    public boolean contains(BigDecimal lat, BigDecimal lng) {
        return minLat.compareTo(lat) <= 0 && lat.compareTo(maxLat) < 0
                && minLng.compareTo(lng) <= 0 && lng.compareTo(maxLng) < 0;
    }
}
