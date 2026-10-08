package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;

public record CreatedCarpool(Long id, Long chatRoomId, int capacity, int currentCount, CompanionStatus status) {
}
