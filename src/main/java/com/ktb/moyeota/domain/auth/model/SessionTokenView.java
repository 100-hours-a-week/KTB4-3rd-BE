package com.ktb.moyeota.domain.auth.model;

import java.time.LocalDateTime;

public record SessionTokenView(
        String tokenHash,
        Long sessionId,
        Long userId,
        LocalDateTime supersededAt,
        LocalDateTime absoluteExpiresAt) {

    public boolean isSuperseded() {
        return supersededAt != null;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(absoluteExpiresAt);
    }
}
