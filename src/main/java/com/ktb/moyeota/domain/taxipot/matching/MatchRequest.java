package com.ktb.moyeota.domain.taxipot.matching;

import java.time.LocalDateTime;

public record MatchRequest(
        long requestId,
        long userId,
        Place origin,
        Place dest,
        LocalDateTime departureAt,
        LocalDateTime requestedAt) {
}
