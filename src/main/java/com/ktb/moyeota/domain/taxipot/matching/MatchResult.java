package com.ktb.moyeota.domain.taxipot.matching;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record MatchResult(
        String resultId,
        String potKey,
        List<MatchRequest> requests,
        Long originAnchorRequestId,
        Long destAnchorRequestId,
        LocalDateTime departureAt) {

    private static final String JOIN_SEPARATOR = ":";

    public MatchResult {
        requests = List.copyOf(requests);
    }

    public static MatchResult openPot(
            List<MatchRequest> requests, long originAnchorRequestId, long destAnchorRequestId,
            LocalDateTime departureAt) {
        String potKey = sortedIds(requests);
        return new MatchResult(potKey, potKey, requests, originAnchorRequestId, destAnchorRequestId, departureAt);
    }

    public static MatchResult joinPot(OpenPot pot, List<MatchRequest> requests) {
        return new MatchResult(
                pot.potKey() + JOIN_SEPARATOR + sortedIds(requests), pot.potKey(), requests, null, null,
                pot.departureAt());
    }

    public boolean opensPot() {
        return resultId.equals(potKey);
    }

    private static String sortedIds(List<MatchRequest> requests) {
        return requests.stream()
                .mapToLong(MatchRequest::requestId)
                .sorted()
                .mapToObj(Long::toString)
                .collect(Collectors.joining(","));
    }
}
