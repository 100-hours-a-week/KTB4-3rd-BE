package com.ktb.moyeota.domain.congestion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum CauseCode {
    RUSH_HOUR,
    EVENT,
    ACCIDENT,
    BREAKDOWN,
    DELAY,
    SERVICE_DISRUPTION,
    WEATHER,
    ROAD_TRAFFIC,
    CONSTRUCTION,
    DEMONSTRATION,
    UNKNOWN;

    @JsonCreator
    public static CauseCode from(String code) {
        return Arrays.stream(values())
                .filter(cause -> cause.name().equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
