package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.math.BigDecimal;

public record MatchPlaceData(String name, BigDecimal lat, BigDecimal lng) {

    private static final int MICRO_SCALE = 6;

    public MatchPlaceData {
        if (lat == null || lng == null) {
            throw new InvalidEventException("장소에 위도 · 경도가 필요합니다. name=" + name);
        }
    }

    public static MatchPlaceData from(Place place) {
        return new MatchPlaceData(place.name(),
                BigDecimal.valueOf(place.point().latMicros(), MICRO_SCALE),
                BigDecimal.valueOf(place.point().lngMicros(), MICRO_SCALE));
    }

    public Place toPlace() {
        return new Place(name, GeoPoint.of(lat, lng));
    }
}
