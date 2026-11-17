package com.ktb.moyeota.domain.companion.entity;

import java.math.BigDecimal;
import java.time.Duration;

public final class StraightLineRideDuration {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double DETOUR_FACTOR = 1.3;
    private static final double AVERAGE_SPEED_KMH = 25.0;
    private static final Duration MIN = Duration.ofMinutes(5);
    private static final Duration MAX = Duration.ofHours(5);

    private StraightLineRideDuration() {
    }

    public static Duration estimate(BigDecimal originLat, BigDecimal originLng, BigDecimal destLat, BigDecimal destLng) {
        double roadKm = distanceKm(originLat.doubleValue(), originLng.doubleValue(),
                destLat.doubleValue(), destLng.doubleValue()) * DETOUR_FACTOR;
        Duration duration = Duration.ofSeconds(Math.round(roadKm / AVERAGE_SPEED_KMH * 3600));
        if (duration.compareTo(MIN) < 0) {
            return MIN;
        }
        if (duration.compareTo(MAX) > 0) {
            return MAX;
        }
        return duration;
    }

    public static double distanceKm(double originLat, double originLng, double destLat, double destLng) {
        double dLat = Math.toRadians(destLat - originLat);
        double dLng = Math.toRadians(destLng - originLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(originLat)) * Math.cos(Math.toRadians(destLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
