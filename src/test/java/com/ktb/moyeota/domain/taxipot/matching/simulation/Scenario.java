package com.ktb.moyeota.domain.taxipot.matching.simulation;

import java.time.LocalTime;

public record Scenario(String name, Spot origin, Spot dest, LocalTime requestFrom, LocalTime requestTo) {

    public Scenario reversed(String name, LocalTime requestFrom, LocalTime requestTo) {
        return new Scenario(name, dest, origin, requestFrom, requestTo);
    }
}
