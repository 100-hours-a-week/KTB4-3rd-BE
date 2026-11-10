package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import java.util.Map;

public record Spot(String name, GeoPoint center, int radiusMeters, Map<PlaceGroup, Double> shares) {

    public Spot {
        shares = Map.copyOf(shares);
    }
}
