package com.ktb.moyeota.global.event;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventEnvelopeFactory {

    private final Clock clock;

    public <T> EventEnvelope<T> create(String eventType, int schemaVersion, T data) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(), eventType, schemaVersion, OffsetDateTime.now(clock), data);
    }
}
