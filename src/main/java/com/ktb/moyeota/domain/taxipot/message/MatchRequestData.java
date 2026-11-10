package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import java.time.LocalDateTime;

public record MatchRequestData(
        long requestId,
        long userId,
        MatchPlaceData origin,
        MatchPlaceData dest,
        LocalDateTime departureAt,
        LocalDateTime requestedAt) {

    public static MatchRequestData from(MatchRequest request) {
        return new MatchRequestData(request.requestId(), request.userId(),
                MatchPlaceData.from(request.origin()), MatchPlaceData.from(request.dest()),
                request.departureAt(), request.requestedAt());
    }

    public MatchRequest toMatchRequest() {
        return new MatchRequest(requestId, userId, origin.toPlace(), dest.toPlace(), departureAt, requestedAt);
    }
}
