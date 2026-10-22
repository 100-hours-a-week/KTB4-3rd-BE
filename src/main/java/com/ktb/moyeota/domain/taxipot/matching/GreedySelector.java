package com.ktb.moyeota.domain.taxipot.matching;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GreedySelector implements CandidateSelector {

    @Override
    public List<MatchResult> select(List<MatchCandidate> ranked) {
        Set<Long> matchedRequestIds = new HashSet<>();
        Set<String> joinedPotKeys = new HashSet<>();
        List<MatchResult> results = new ArrayList<>();
        for (MatchCandidate candidate : ranked) {
            MatchResult result = candidate.result();
            boolean requestTaken = result.requests().stream()
                    .anyMatch(request -> matchedRequestIds.contains(request.requestId()));
            boolean potTaken = !result.opensPot() && joinedPotKeys.contains(result.potKey());
            if (requestTaken || potTaken) {
                continue;
            }
            result.requests().forEach(request -> matchedRequestIds.add(request.requestId()));
            if (!result.opensPot()) {
                joinedPotKeys.add(result.potKey());
            }
            results.add(result);
        }
        return results;
    }
}
