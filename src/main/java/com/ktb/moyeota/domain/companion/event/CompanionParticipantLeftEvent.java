package com.ktb.moyeota.domain.companion.event;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;

public record CompanionParticipantLeftEvent(Long companionId, CompanionKind kind, Long userId) {
}
