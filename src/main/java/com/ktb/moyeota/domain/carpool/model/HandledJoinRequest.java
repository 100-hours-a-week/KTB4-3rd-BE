package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;

public record HandledJoinRequest(
        Long id, CompanionRequestStatus status, Long chatRoomId, Integer currentCount, Integer capacity) {

    public static HandledJoinRequest rejected(Long id) {
        return new HandledJoinRequest(id, CompanionRequestStatus.REJECTED, null, null, null);
    }
}
