package com.ktb.moyeota.domain.taxipot.message;

import java.util.List;

public record MatchRideStartedData(List<Long> requestIds) {

    public MatchRideStartedData {
        requestIds = List.copyOf(requestIds);
    }
}
