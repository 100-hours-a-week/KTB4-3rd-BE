package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.BatchKey;
import com.ktb.moyeota.domain.taxipot.matching.BatchMatcher;
import com.ktb.moyeota.domain.taxipot.matching.InstantMatcher;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.OpenPot;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.matching.RegionGrid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MatchingSimulator {

    private static final RegionCell WHOLE_MAP = new RegionCell(0, 0);

    public SimulationResult run(List<MatchRequest> flow, MatchingMethod method) {
        Map<String, Pot> pots = new LinkedHashMap<>();
        Map<Long, LocalDateTime> matchedAt = new HashMap<>();
        if (method.batched()) {
            runBatch(flow, method, pots, matchedAt);
        } else {
            runInstant(flow, method, pots, matchedAt);
        }
        return summarize(flow, method, pots, matchedAt);
    }

    private void runInstant(
            List<MatchRequest> flow, MatchingMethod method, Map<String, Pot> pots, Map<Long, LocalDateTime> matchedAt) {
        InstantMatcher matcher = new InstantMatcher(method.rules());
        Map<LocalDateTime, List<Pot>> potsByDeparture = new HashMap<>();
        for (MatchRequest request : flow) {
            List<Pot> sameDeparture = potsByDeparture.computeIfAbsent(request.departureAt(), key -> new ArrayList<>());
            MatchResult result = matcher.match(request, openPots(sameDeparture, method));
            Pot pot = apply(result, pots, matchedAt, request.requestedAt());
            if (result.opensPot()) {
                sameDeparture.add(pot);
            }
        }
    }

    private void runBatch(
            List<MatchRequest> flow, MatchingMethod method, Map<String, Pot> pots, Map<Long, LocalDateTime> matchedAt) {
        if (flow.isEmpty()) {
            return;
        }
        BatchMatcher matcher = new BatchMatcher(method.rules(), method.neighborLimit(), method.selector());
        RegionGrid grid = method.cellMeters() == 0 ? null : new RegionGrid(method.cellMeters());
        Map<BatchKey, List<Pot>> potsByKey = new HashMap<>();
        List<MatchRequest> waiting = new ArrayList<>();
        LocalDateTime lastArrival = flow.get(flow.size() - 1).requestedAt();
        int next = 0;
        for (LocalDateTime tick = firstTick(flow.get(0).requestedAt(), method.batchIntervalSeconds());
                !tick.isAfter(lastArrival.plusSeconds(method.batchIntervalSeconds()));
                tick = tick.plusSeconds(method.batchIntervalSeconds())) {
            while (next < flow.size() && !flow.get(next).requestedAt().isAfter(tick)) {
                waiting.add(flow.get(next++));
            }
            LocalDateTime now = tick;
            waiting.removeIf(request -> !request.departureAt().isAfter(now));
            Map<BatchKey, List<MatchRequest>> byKey = new LinkedHashMap<>();
            for (MatchRequest request : waiting) {
                byKey.computeIfAbsent(keyOf(request, grid), key -> new ArrayList<>()).add(request);
            }
            for (Map.Entry<BatchKey, List<MatchRequest>> batch : byKey.entrySet()) {
                List<Pot> keyPots = potsByKey.computeIfAbsent(batch.getKey(), key -> new ArrayList<>());
                for (MatchResult result : matcher.match(batch.getValue(), openPots(keyPots, method))) {
                    Pot pot = apply(result, pots, matchedAt, now);
                    if (result.opensPot()) {
                        keyPots.add(pot);
                    }
                    waiting.removeAll(result.requests());
                }
            }
        }
    }

    private LocalDateTime firstTick(LocalDateTime firstArrival, int intervalSeconds) {
        LocalDateTime midnight = firstArrival.toLocalDate().atStartOfDay();
        long seconds = Duration.between(midnight, firstArrival).toSeconds();
        long ticks = (seconds + intervalSeconds - 1) / intervalSeconds;
        return midnight.plusSeconds(ticks * intervalSeconds);
    }

    private BatchKey keyOf(MatchRequest request, RegionGrid grid) {
        return grid == null ? new BatchKey(WHOLE_MAP, request.departureAt()) : grid.batchKeyOf(request);
    }

    private List<OpenPot> openPots(List<Pot> candidates, MatchingMethod method) {
        return candidates.stream()
                .filter(pot -> pot.members.size() < method.rules().capacity())
                .map(pot -> new OpenPot(pot.potKey, pot.departureAt, pot.members))
                .toList();
    }

    private Pot apply(
            MatchResult result, Map<String, Pot> pots, Map<Long, LocalDateTime> matchedAt, LocalDateTime now) {
        Pot pot = pots.computeIfAbsent(result.potKey(), potKey -> new Pot(potKey, result.departureAt()));
        pot.members.addAll(result.requests());
        if (pot.members.size() >= 2) {
            pot.members.forEach(member -> matchedAt.putIfAbsent(member.requestId(), now));
        }
        return pot;
    }

    private SimulationResult summarize(
            List<MatchRequest> flow, MatchingMethod method, Map<String, Pot> pots, Map<Long, LocalDateTime> matchedAt) {
        List<Pot> shared = pots.values().stream().filter(pot -> pot.members.size() >= 2).toList();
        double averagePotSize = shared.stream().mapToInt(pot -> pot.members.size()).average().orElse(0);
        List<Duration> waits = flow.stream()
                .filter(request -> matchedAt.containsKey(request.requestId()))
                .map(request -> Duration.between(request.requestedAt(), matchedAt.get(request.requestId())))
                .sorted()
                .toList();
        double aloneRatio = flow.isEmpty() ? 0 : 1.0 - (double) waits.size() / flow.size();
        return new SimulationResult(method.name(), flow.size(), waits.size(), shared.size(), averagePotSize,
                aloneRatio, percentile(waits, 0.5), percentile(waits, 0.95));
    }

    private Duration percentile(List<Duration> sorted, double ratio) {
        if (sorted.isEmpty()) {
            return Duration.ZERO;
        }
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static final class Pot {

        private final String potKey;
        private final LocalDateTime departureAt;
        private final List<MatchRequest> members = new ArrayList<>();

        private Pot(String potKey, LocalDateTime departureAt) {
            this.potKey = potKey;
            this.departureAt = departureAt;
        }
    }
}
