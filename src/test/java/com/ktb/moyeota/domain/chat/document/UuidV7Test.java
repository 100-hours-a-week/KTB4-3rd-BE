package com.ktb.moyeota.domain.chat.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    @DisplayName("버전 7 · RFC 9562 변형이고 앞 48비트에 밀리초 시각을 담는다")
    void encodesMillisAsVersion7() {
        Instant at = Instant.parse("2026-11-11T10:00:00.123Z");

        UUID id = UuidV7.at(at);

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(at.toEpochMilli());
    }

    @Test
    @DisplayName("같은 밀리초에 만들어도 서로 다른 값이 나온다")
    void differsWithinSameMillis() {
        Instant at = Instant.parse("2026-11-11T10:00:00.123Z");

        assertThat(UuidV7.at(at)).isNotEqualTo(UuidV7.at(at));
    }
}
