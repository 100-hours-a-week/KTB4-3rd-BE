package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.CandidateSelector;
import com.ktb.moyeota.domain.taxipot.matching.ExactSelector;
import com.ktb.moyeota.domain.taxipot.matching.GreedySelector;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;

public record MatchingMethod(
        String name, MatchingRules rules, int batchIntervalSeconds, int cellMeters, int neighborLimit,
        CandidateSelector selector) {

    private static final int CAPACITY = 4;

    public static MatchingMethod exact() {
        return new MatchingMethod("완전 일치", new MatchingRules(0, CAPACITY), 0, 0, 0, null);
    }

    public static MatchingMethod instant(int radiusMeters) {
        return new MatchingMethod("즉시 " + radiusMeters + "m", new MatchingRules(radiusMeters, CAPACITY), 0, 0, 0, null);
    }

    public static MatchingMethod batch(int intervalSeconds, int cellMeters, int neighborLimit, int radiusMeters) {
        return batch(intervalSeconds, cellMeters, neighborLimit, radiusMeters, new ExactSelector());
    }

    public static MatchingMethod batch(
            int intervalSeconds, int cellMeters, int neighborLimit, int radiusMeters, CandidateSelector selector) {
        String grid = cellMeters == 0 ? "격자 없음" : cellMeters / 1_000.0 + "km 격자";
        String selection = selector instanceof GreedySelector ? " · 탐욕" : "";
        return new MatchingMethod("묶음 " + intervalSeconds + "초 · " + grid + " · " + radiusMeters + "m" + selection,
                new MatchingRules(radiusMeters, CAPACITY), intervalSeconds, cellMeters, neighborLimit, selector);
    }

    public boolean batched() {
        return batchIntervalSeconds > 0;
    }
}
