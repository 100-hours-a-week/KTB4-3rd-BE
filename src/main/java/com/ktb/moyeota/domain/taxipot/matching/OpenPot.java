package com.ktb.moyeota.domain.taxipot.matching;

import java.time.LocalDateTime;
import java.util.List;

public record OpenPot(String potKey, LocalDateTime departureAt, List<MatchRequest> members) {

    public OpenPot {
        members = List.copyOf(members);
    }
}
