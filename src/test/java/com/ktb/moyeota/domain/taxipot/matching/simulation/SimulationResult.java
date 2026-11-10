package com.ktb.moyeota.domain.taxipot.matching.simulation;

import java.time.Duration;

public record SimulationResult(
        String method,
        int requests,
        int matchedRequests,
        int pots,
        double averagePotSize,
        double aloneRatio,
        Duration medianWait,
        Duration p95Wait) {
}
