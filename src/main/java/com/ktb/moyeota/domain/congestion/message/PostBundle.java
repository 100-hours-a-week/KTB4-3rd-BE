package com.ktb.moyeota.domain.congestion.message;

import java.time.OffsetDateTime;
import java.util.List;

public record PostBundle(
        Long postId,
        String title,
        String content,
        String authorKey,
        OffsetDateTime createdAt,
        boolean contextOnly,
        List<BundleComment> comments) {

    public PostBundle {
        comments = List.copyOf(comments);
    }
}
