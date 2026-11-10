package com.ktb.moyeota.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private static final String EVENT_ID = "0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90";
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-10-28T18:42:10+09:00");
    private static final RideEnded DATA = new RideEnded(55L);

    @Test
    @DisplayName("봉투 필수 값이 하나라도 비면 잘못된 이벤트로 본다")
    void rejectsMissingFields() {
        assertThatThrownBy(() -> new EventEnvelope<>(null, "ride.ended", 1, OCCURRED_AT, DATA))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(EVENT_ID, " ", 1, OCCURRED_AT, DATA))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(EVENT_ID, "ride.ended", 0, OCCURRED_AT, DATA))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(EVENT_ID, "ride.ended", 1, null, DATA))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(EVENT_ID, "ride.ended", 1, OCCURRED_AT, null))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    @DisplayName("리스너가 읽을 수 있는 버전이면 그대로 돌려주고, 아니면 잘못된 이벤트로 본다")
    void checksSchemaVersion() {
        EventEnvelope<RideEnded> version2 = new EventEnvelope<>(EVENT_ID, "ride.ended", 2, OCCURRED_AT, DATA);

        assertThat(version2.requireSchemaVersion(1, 2)).isSameAs(version2);
        assertThatThrownBy(() -> version2.requireSchemaVersion(1))
                .isInstanceOf(InvalidEventException.class);
    }

    record RideEnded(Long companionId) {
    }
}
