package com.ktb.moyeota.domain.taxipot.stream;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record CellPot(String potId, LocalDateTime departureAt, List<Long> memberIds) {

    public CellPot {
        memberIds = List.copyOf(memberIds);
    }

    public String potKey() {
        return memberIds.stream()
                .sorted()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    CellPot with(Collection<Long> joined) {
        return new CellPot(potId, departureAt, Stream.concat(memberIds.stream(), joined.stream()).toList());
    }

    CellPot without(long requestId) {
        return new CellPot(potId, departureAt, memberIds.stream().filter(id -> id != requestId).toList());
    }
}
