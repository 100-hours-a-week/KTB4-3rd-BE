package com.ktb.moyeota.domain.taxipot.model;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;

public record CurrentTaxiPot(Long id, Long chatRoomId, CompanionStatus status, int currentCount, int capacity) {
}
