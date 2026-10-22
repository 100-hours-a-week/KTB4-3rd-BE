package com.ktb.moyeota.domain.taxipot.matching;

import java.math.BigDecimal;

public record GeoPoint(long latMicros, long lngMicros) {

    private static final int MICRO_SCALE = 6;

    public static GeoPoint of(BigDecimal lat, BigDecimal lng) {
        return new GeoPoint(toMicros(lat), toMicros(lng));
    }

    private static long toMicros(BigDecimal degrees) {
        return degrees.movePointRight(MICRO_SCALE).longValueExact();
    }
}
