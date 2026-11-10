package com.ktb.moyeota.domain.taxipot.matching;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public class BatchMatcher {

    private static final Comparator<MatchRequest> BY_REQUEST_ID = Comparator.comparingLong(MatchRequest::requestId);
    private static final Comparator<MatchCandidate> PRIORITY = Comparator
            .comparingInt(MatchCandidate::potSize).reversed()
            .thenComparingDouble(MatchCandidate::spreadMeters)
            .thenComparing(candidate -> candidate.result().resultId());

    private final MatchingRules rules;
    private final MatchingCondition condition;
    private final int neighborLimit;
    private final CandidateSelector selector;

    public BatchMatcher(MatchingRules rules, int neighborLimit) {
        this(rules, neighborLimit, new ExactSelector());
    }

    public BatchMatcher(MatchingRules rules, int neighborLimit, CandidateSelector selector) {
        this.rules = rules;
        this.condition = new MatchingCondition(rules);
        this.neighborLimit = neighborLimit;
        this.selector = selector;
    }

    public List<MatchResult> match(List<MatchRequest> waiting, List<OpenPot> openPots) {
        List<MatchResult> results = new ArrayList<>();
        List<MatchRequest> rest = fillSamePlaces(waiting.stream().sorted(BY_REQUEST_ID).toList(), results);

        Map<String, MatchCandidate> candidates = new LinkedHashMap<>();
        for (MatchRequest seed : rest) {
            List<MatchRequest> neighbors = nearest(rest, List.of(seed), other -> distance(seed, other));
            collect(null, List.of(seed), neighbors, 0, new ArrayList<>(), candidates);
        }
        for (OpenPot pot : openPots) {
            List<MatchRequest> neighbors = nearest(rest, pot.members(), other -> distanceToPot(pot, other));
            collect(pot, pot.members(), neighbors, 0, new ArrayList<>(), candidates);
        }
        results.addAll(selector.select(candidates.values().stream().sorted(PRIORITY).toList()));
        return results;
    }

    private List<MatchRequest> fillSamePlaces(List<MatchRequest> sorted, List<MatchResult> results) {
        Map<SamePlace, List<MatchRequest>> buckets = new LinkedHashMap<>();
        for (MatchRequest request : sorted) {
            buckets.computeIfAbsent(SamePlace.of(request), key -> new ArrayList<>()).add(request);
        }
        List<MatchRequest> rest = new ArrayList<>();
        for (List<MatchRequest> bucket : buckets.values()) {
            int full = bucket.size() - bucket.size() % rules.capacity();
            for (int from = 0; from < full; from += rules.capacity()) {
                List<MatchRequest> group = bucket.subList(from, from + rules.capacity());
                long anchor = group.get(0).requestId();
                results.add(MatchResult.openPot(group, anchor, anchor, group.get(0).departureAt()));
            }
            rest.addAll(bucket.subList(full, bucket.size()));
        }
        rest.sort(BY_REQUEST_ID);
        return rest;
    }

    private List<MatchRequest> nearest(
            List<MatchRequest> pool, List<MatchRequest> fixed, ToDoubleFunction<MatchRequest> distance) {
        return pool.stream()
                .filter(other -> !fixed.contains(other) && closeToAll(other, fixed))
                .sorted(Comparator.comparingDouble(distance).thenComparing(BY_REQUEST_ID))
                .limit(neighborLimit)
                .toList();
    }

    private double distance(MatchRequest from, MatchRequest to) {
        return GeoDistance.meters(from.origin().point(), to.origin().point())
                + GeoDistance.meters(from.dest().point(), to.dest().point());
    }

    private double distanceToPot(OpenPot pot, MatchRequest request) {
        return pot.members().stream().mapToDouble(member -> distance(member, request)).max().orElse(0);
    }

    private void collect(
            OpenPot pot, List<MatchRequest> fixed, List<MatchRequest> options, int from,
            List<MatchRequest> picked, Map<String, MatchCandidate> candidates) {
        for (int i = from; i < options.size(); i++) {
            MatchRequest request = options.get(i);
            if (!closeToAll(request, picked)) {
                continue;
            }
            picked.add(request);
            addIfGroupable(pot, fixed, picked, candidates);
            if (fixed.size() + picked.size() < rules.capacity()) {
                collect(pot, fixed, options, i + 1, picked, candidates);
            }
            picked.remove(picked.size() - 1);
        }
    }

    private boolean closeToAll(MatchRequest request, List<MatchRequest> others) {
        double reach = 2.0 * rules.radiusMeters();
        return others.stream().allMatch(other ->
                GeoDistance.meters(request.origin().point(), other.origin().point()) <= reach
                        && GeoDistance.meters(request.dest().point(), other.dest().point()) <= reach);
    }

    private void addIfGroupable(
            OpenPot pot, List<MatchRequest> fixed, List<MatchRequest> picked, Map<String, MatchCandidate> candidates) {
        List<MatchRequest> group = Stream.concat(fixed.stream(), picked.stream()).sorted(BY_REQUEST_ID).toList();
        condition.evaluate(group).ifPresent(fit -> {
            MatchResult result = pot == null
                    ? MatchResult.openPot(group, fit.origin().requestId(), fit.dest().requestId(),
                            group.get(0).departureAt())
                    : MatchResult.joinPot(pot, picked.stream().sorted(BY_REQUEST_ID).toList());
            candidates.putIfAbsent(result.resultId(), new MatchCandidate(
                    result, group.size(), fit.origin().spreadMeters() + fit.dest().spreadMeters()));
        });
    }

    private record SamePlace(GeoPoint origin, GeoPoint dest, LocalDateTime departureAt) {

        static SamePlace of(MatchRequest request) {
            return new SamePlace(request.origin().point(), request.dest().point(), request.departureAt());
        }
    }
}
