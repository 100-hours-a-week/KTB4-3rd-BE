package com.ktb.moyeota.domain.congestion.window;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CongestionWindowCalculatorTest {

    private final CongestionWindowCalculator calculator =
            new CongestionWindowCalculator(new CongestionWindowProperties(7, 10, 17, 20));

    @ParameterizedTest(name = "평일 {0} 은 {1} ~ {2} 구간이다")
    @CsvSource({
            "2026-11-02T06:30:00+09:00, 2026-11-02T06:00+09:00, 2026-11-02T07:00+09:00",
            "2026-11-02T07:00:00+09:00, 2026-11-02T07:00+09:00, 2026-11-02T07:10+09:00",
            "2026-11-02T07:09:59+09:00, 2026-11-02T07:00+09:00, 2026-11-02T07:10+09:00",
            "2026-11-02T09:55:00+09:00, 2026-11-02T09:50+09:00, 2026-11-02T10:00+09:00",
            "2026-11-02T10:00:00+09:00, 2026-11-02T10:00+09:00, 2026-11-02T11:00+09:00",
            "2026-11-02T16:59:59+09:00, 2026-11-02T16:00+09:00, 2026-11-02T17:00+09:00",
            "2026-11-02T17:15:00+09:00, 2026-11-02T17:10+09:00, 2026-11-02T17:20+09:00",
            "2026-11-02T19:59:59+09:00, 2026-11-02T19:50+09:00, 2026-11-02T20:00+09:00",
            "2026-11-02T20:00:00+09:00, 2026-11-02T20:00+09:00, 2026-11-02T21:00+09:00"
    })
    void weekdayWindows(String time, String start, String end) {
        CongestionWindow window = calculator.windowContaining(at(time));

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse(start));
        assertThat(window.end()).isEqualTo(OffsetDateTime.parse(end));
    }

    @Test
    @DisplayName("주말은 출퇴근 시간대도 60분 구간이다")
    void weekendUsesHourlyWindow() {
        CongestionWindow window = calculator.windowContaining(at("2026-11-07T07:05:00+09:00"));

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2026-11-07T07:00+09:00"));
        assertThat(window.end()).isEqualTo(OffsetDateTime.parse("2026-11-07T08:00+09:00"));
    }

    @Test
    @DisplayName("23시 구간은 다음 날 0시에 끝난다")
    void lastWindowEndsAtNextMidnight() {
        CongestionWindow window = calculator.windowContaining(at("2026-11-02T23:30:00+09:00"));

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2026-11-02T23:00+09:00"));
        assertThat(window.end()).isEqualTo(OffsetDateTime.parse("2026-11-03T00:00+09:00"));
    }

    @Test
    @DisplayName("UTC 로 들어온 시각도 한국 시각으로 출퇴근을 판단한다")
    void judgesCommuteInSeoulTime() {
        CongestionWindow window = calculator.windowContaining(Instant.parse("2026-11-02T22:05:00Z"));

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2026-11-03T07:00+09:00"));
        assertThat(window.end()).isEqualTo(OffsetDateTime.parse("2026-11-03T07:10+09:00"));
    }

    private static Instant at(String time) {
        return OffsetDateTime.parse(time).toInstant();
    }
}
