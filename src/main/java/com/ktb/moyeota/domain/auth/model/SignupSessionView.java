package com.ktb.moyeota.domain.auth.model;

import java.time.LocalDateTime;

public record SignupSessionView(
        String tokenHash,
        OAuthProvider provider,
        String providerUserId,
        LocalDateTime expiresAt) {

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
