package com.ktb.moyeota.domain.congestion.message;

import java.time.LocalDateTime;

public record ChangedComment(
        Long commentId,
        Long authorId,
        String content,
        LocalDateTime createdAt) {
}
