package com.ktb.moyeota.domain.congestion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum CauseCode {
    RUSH_HOUR,
    EVENT,
    ACCIDENT,
    SERVICE_ISSUE,
    DELAY_ISSUE,
    UNKNOWN;

    @JsonCreator
    public static CauseCode from(String code) {
        return Arrays.stream(values())
                .filter(cause -> cause.name().equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
