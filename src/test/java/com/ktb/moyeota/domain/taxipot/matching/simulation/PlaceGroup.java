package com.ktb.moyeota.domain.taxipot.matching.simulation;

import java.util.Set;

public enum PlaceGroup {

    STATION,
    BUILDING,
    OTHER;

    private static final Set<String> BUILDING_CATEGORIES = Set.of("PO3", "SC4", "HP8", "CT1", "AT4", "MT1", "AD5");

    public static PlaceGroup of(SimPlace place) {
        if ("SW8".equals(place.category())) {
            return STATION;
        }
        if (place.category() == null || BUILDING_CATEGORIES.contains(place.category())) {
            return BUILDING;
        }
        return OTHER;
    }
}
