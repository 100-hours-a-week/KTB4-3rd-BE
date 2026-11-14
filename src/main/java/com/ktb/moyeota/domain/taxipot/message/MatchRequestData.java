package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.time.LocalDateTime;

public record MatchRequestData(
        long requestId,
        long userId,
        MatchPlaceData origin,
        MatchPlaceData dest,
        LocalDateTime departureAt,
        LocalDateTime requestedAt) {

    public MatchRequestData {
        if (origin == null || dest == null || departureAt == null || requestedAt == null) {
            throw new InvalidEventException("매칭 요청에 출발지 · 도착지 · 출발 시각 · 요청 시각이 필요합니다. request_id=" + requestId);
        }
    }

    public static MatchRequestData from(MatchRequest request) {
        return new MatchRequestData(request.requestId(), request.userId(),
                MatchPlaceData.from(request.origin()), MatchPlaceData.from(request.dest()),
                request.departureAt(), request.requestedAt());
    }

    public MatchRequest toMatchRequest() {
        return new MatchRequest(requestId, userId, origin.toPlace(), dest.toPlace(), departureAt, requestedAt);
    }
}
