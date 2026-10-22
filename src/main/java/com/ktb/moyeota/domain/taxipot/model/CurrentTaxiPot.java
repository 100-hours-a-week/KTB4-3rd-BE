package com.ktb.moyeota.domain.taxipot.model;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;

public record CurrentTaxiPot(Long id, Long chatRoomId, CompanionStatus status, Integer currentCount, int capacity) {

    private static final int TAXI_POT_CAPACITY = 4;

    public static CurrentTaxiPot matching() {
        return new CurrentTaxiPot(null, null, null, null, TAXI_POT_CAPACITY);
    }

    public boolean isMatching() {
        return id == null;
    }
}
