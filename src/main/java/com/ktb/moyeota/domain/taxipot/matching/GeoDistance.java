package com.ktb.moyeota.domain.taxipot.matching;

public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_008.7714;
    private static final double MICROS_PER_DEGREE = 1_000_000;

    private GeoDistance() {
    }

    public static double meters(GeoPoint from, GeoPoint to) {
        double fromLat = radians(from.latMicros());
        double toLat = radians(to.latMicros());
        double halfLat = (toLat - fromLat) / 2;
        double halfLng = (radians(to.lngMicros()) - radians(from.lngMicros())) / 2;
        double h = Math.sin(halfLat) * Math.sin(halfLat)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(halfLng) * Math.sin(halfLng);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static double radians(long micros) {
        return Math.toRadians(micros / MICROS_PER_DEGREE);
    }
}
