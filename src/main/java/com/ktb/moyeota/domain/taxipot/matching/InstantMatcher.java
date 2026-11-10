package com.ktb.moyeota.domain.taxipot.matching;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class InstantMatcher {

    private static final Comparator<Candidate> NEAREST = Comparator
            .comparingDouble(Candidate::spreadMeters)
            .thenComparing(candidate -> candidate.result().resultId());

    private final MatchingCondition condition;

    public InstantMatcher(MatchingRules rules) {
        this.condition = new MatchingCondition(rules);
    }

    public MatchResult match(MatchRequest request, List<OpenPot> openPots) {
        return openPots.stream()
                .map(pot -> join(request, pot))
                .flatMap(Optional::stream)
                .min(NEAREST)
                .map(Candidate::result)
                .orElseGet(() -> MatchResult.openPot(
                        List.of(request), request.requestId(), request.requestId(), request.departureAt()));
    }

    private Optional<Candidate> join(MatchRequest request, OpenPot pot) {
        List<MatchRequest> group = Stream.concat(pot.members().stream(), Stream.of(request)).toList();
        return condition.evaluate(group).map(fit -> new Candidate(
                MatchResult.joinPot(pot, List.of(request)),
                fit.origin().spreadMeters() + fit.dest().spreadMeters()));
    }

    private record Candidate(MatchResult result, double spreadMeters) {
    }
}
