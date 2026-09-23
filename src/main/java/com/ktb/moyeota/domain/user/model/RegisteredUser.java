package com.ktb.moyeota.domain.user.model;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import java.time.LocalDateTime;

public record RegisteredUser(
        Long userId, String profileImageUrl, LocalDateTime createdAt, IssuedSession session) {
}
