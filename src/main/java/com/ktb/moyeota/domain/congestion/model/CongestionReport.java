package com.ktb.moyeota.domain.congestion.model;

import java.util.Map;

public record CongestionReport(CongestionLevel level, Map<CauseCode, Integer> causeReporters) {

    public CongestionReport {
        causeReporters = Map.copyOf(causeReporters);
    }

    public int reportersOf(CauseCode causeCode) {
        return causeReporters.getOrDefault(causeCode, 0);
    }
}
