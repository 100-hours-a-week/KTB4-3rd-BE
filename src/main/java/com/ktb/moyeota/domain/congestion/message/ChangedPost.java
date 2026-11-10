package com.ktb.moyeota.domain.congestion.message;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChangedPost(
        Long postId,
        Long authorId,
        String title,
        String content,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime createdAt) {
}
