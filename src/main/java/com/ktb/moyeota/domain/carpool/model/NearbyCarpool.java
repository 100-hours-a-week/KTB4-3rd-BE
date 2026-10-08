package com.ktb.moyeota.domain.carpool.model;

import java.time.LocalDateTime;

public record NearbyCarpool(
        Long id,
        String hostNickname,
        String hostProfileImageUrl,
        String originName,
        String destName,
        LocalDateTime departureAt,
        double distanceM,
        int currentCount,
        int capacity,
        boolean full,
        boolean expired) {
}
