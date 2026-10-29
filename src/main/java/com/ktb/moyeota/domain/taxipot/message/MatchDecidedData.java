package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import java.time.LocalDateTime;
import java.util.List;

public record MatchDecidedData(
        String resultId,
        String potKey,
        List<MatchRequestData> requests,
        Long originAnchorRequestId,
        Long destAnchorRequestId,
        LocalDateTime departureAt) {

    public MatchDecidedData {
        requests = List.copyOf(requests);
    }

    public static MatchDecidedData from(MatchResult result) {
        return new MatchDecidedData(result.resultId(), result.potKey(),
                result.requests().stream().map(MatchRequestData::from).toList(),
                result.originAnchorRequestId(), result.destAnchorRequestId(), result.departureAt());
    }

    public MatchResult toMatchResult() {
        return new MatchResult(resultId, potKey,
                requests.stream().map(MatchRequestData::toMatchRequest).toList(),
                originAnchorRequestId, destAnchorRequestId, departureAt);
    }
}
