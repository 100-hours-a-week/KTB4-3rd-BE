package com.ktb.moyeota.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventEnvelopeFactoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:42:10Z"), ZoneId.of("Asia/Seoul"));

    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(CLOCK);

    @Test
    @DisplayName("종류 · 버전 · 내용을 그대로 담고, 고유 번호와 지금 시각을 채운다")
    void createsEnvelope() {
        EventEnvelope<RideEnded> envelope = factory.create("ride.ended", 1, new RideEnded(55L));

        assertThat(UUID.fromString(envelope.eventId())).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("ride.ended");
        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-10-28T18:42:10+09:00"));
        assertThat(envelope.data()).isEqualTo(new RideEnded(55L));
    }

    @Test
    @DisplayName("만들 때마다 다른 고유 번호를 붙인다")
    void givesDistinctEventIds() {
        EventEnvelope<RideEnded> first = factory.create("ride.ended", 1, new RideEnded(55L));
        EventEnvelope<RideEnded> second = factory.create("ride.ended", 1, new RideEnded(55L));

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    record RideEnded(Long companionId) {
    }
}
