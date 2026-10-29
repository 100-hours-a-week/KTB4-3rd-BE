package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import java.math.BigDecimal;

public record MatchPlaceData(String name, BigDecimal lat, BigDecimal lng) {

    private static final int MICRO_SCALE = 6;

    public static MatchPlaceData from(Place place) {
        return new MatchPlaceData(place.name(),
                BigDecimal.valueOf(place.point().latMicros(), MICRO_SCALE),
                BigDecimal.valueOf(place.point().lngMicros(), MICRO_SCALE));
    }

    public Place toPlace() {
        return new Place(name, GeoPoint.of(lat, lng));
    }
}
