package com.ktb.moyeota.domain.chat.repository;

import java.time.Instant;
import org.springframework.data.annotation.Id;

public record LastMessage(
        @Id Long roomId,
        long lastSeq,
        Instant lastMessageAt
) {
}
