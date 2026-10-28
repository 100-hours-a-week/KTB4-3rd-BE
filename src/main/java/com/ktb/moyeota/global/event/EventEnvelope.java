package com.ktb.moyeota.global.event;

import java.time.OffsetDateTime;
import java.util.Arrays;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        T data) {

    public EventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidEventException("event_id 가 없습니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidEventException("event_type 이 없습니다. event_id=" + eventId);
        }
        if (schemaVersion < 1) {
            throw new InvalidEventException("schema_version 이 없습니다. event_id=" + eventId);
        }
        if (occurredAt == null) {
            throw new InvalidEventException("occurred_at 이 없습니다. event_id=" + eventId);
        }
        if (data == null) {
            throw new InvalidEventException("data 가 없습니다. event_id=" + eventId);
        }
    }

    public EventEnvelope<T> requireSchemaVersion(int... supportedVersions) {
        if (Arrays.stream(supportedVersions).noneMatch(version -> version == schemaVersion)) {
            throw new InvalidEventException(
                    "지원하지 않는 schema_version 입니다. event_id=" + eventId + " schema_version=" + schemaVersion);
        }
        return this;
    }
}
