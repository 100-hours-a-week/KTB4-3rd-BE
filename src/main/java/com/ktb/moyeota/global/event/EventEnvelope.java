package com.ktb.moyeota.global.event;

import java.time.OffsetDateTime;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        T data) {
}
