package com.ktb.moyeota.domain.taxipot.matching;

import com.ktb.moyeota.domain.taxipot.matching.GroupFit.Anchor;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MatchingCondition {

    private final MatchingRules rules;

    public MatchingCondition(MatchingRules rules) {
        this.rules = rules;
    }

    public Optional<GroupFit> evaluate(List<MatchRequest> requests) {
        if (requests.isEmpty() || requests.size() > rules.capacity() || !sameDeparture(requests)) {
            return Optional.empty();
        }
        Optional<Anchor> origin = anchor(requests, MatchRequest::origin);
        Optional<Anchor> dest = anchor(requests, MatchRequest::dest);
        if (origin.isEmpty() || dest.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GroupFit(origin.get(), dest.get()));
    }

    private boolean sameDeparture(List<MatchRequest> requests) {
        return requests.stream()
                .map(MatchRequest::departureAt)
                .distinct()
                .count() == 1;
    }

    private Optional<Anchor> anchor(List<MatchRequest> requests, Function<MatchRequest, Place> place) {
        return requests.stream()
                .map(candidate -> new Anchor(candidate.requestId(), spread(candidate, requests, place)))
                .filter(anchor -> anchor.spreadMeters() <= rules.radiusMeters())
                .min(Comparator.comparingDouble(Anchor::spreadMeters).thenComparingLong(Anchor::requestId));
    }

    private double spread(MatchRequest candidate, List<MatchRequest> requests, Function<MatchRequest, Place> place) {
        GeoPoint center = place.apply(candidate).point();
        return requests.stream()
                .mapToDouble(other -> GeoDistance.meters(center, place.apply(other).point()))
                .max()
                .orElse(0);
    }
}
