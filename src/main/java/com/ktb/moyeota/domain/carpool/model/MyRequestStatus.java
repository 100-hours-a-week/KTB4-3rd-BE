package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;

public enum MyRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED;

    public static MyRequestStatus of(CompanionRequestStatus stored, Companion carpool, LocalDateTime now) {
        return switch (stored) {
            case PENDING -> isClosed(carpool, now) ? EXPIRED : PENDING;
            case ACCEPTED -> ACCEPTED;
            case REJECTED -> REJECTED;
        };
    }

    private static boolean isClosed(Companion carpool, LocalDateTime now) {
        return carpool.getStatus() != CompanionStatus.RECRUITING || carpool.getDepartureAt().isBefore(now);
    }
}
