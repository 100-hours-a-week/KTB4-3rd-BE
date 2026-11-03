package com.ktb.moyeota.domain.congestion.message;

import java.time.OffsetDateTime;

public record BundleComment(Long commentId, String content, String authorKey, OffsetDateTime createdAt) {
}
