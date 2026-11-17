package com.ktb.moyeota.domain.companion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StraightLineRideDurationTest {

    private static final BigDecimal PANGYO_LAT = new BigDecimal("37.394500");
    private static final BigDecimal PANGYO_LNG = new BigDecimal("127.111200");
    private static final BigDecimal GANGNAM_LAT = new BigDecimal("37.497900");
    private static final BigDecimal GANGNAM_LNG = new BigDecimal("127.027600");

    @Test
    @DisplayName("두 좌표의 직선거리를 하버사인 공식으로 구한다")
    void measuresStraightLineDistance() {
        double km = StraightLineRideDuration.distanceKm(
                PANGYO_LAT.doubleValue(), PANGYO_LNG.doubleValue(),
                GANGNAM_LAT.doubleValue(), GANGNAM_LNG.doubleValue());

        assertThat(km).isCloseTo(13.6, within(0.1));
    }

    @Test
    @DisplayName("직선거리에 우회 계수 1.3 을 곱해 시속 25km 로 달리는 시간으로 어림한다")
    void estimatesFromDetouredDistance() {
        Duration duration = StraightLineRideDuration.estimate(PANGYO_LAT, PANGYO_LNG, GANGNAM_LAT, GANGNAM_LNG);

        assertThat(duration.toMinutes()).isBetween(42L, 43L);
    }

    @Test
    @DisplayName("아주 가까우면 최소 5분으로 잡는다")
    void hasMinimum() {
        Duration duration = StraightLineRideDuration.estimate(
                PANGYO_LAT, PANGYO_LNG, new BigDecimal("37.395000"), new BigDecimal("127.111200"));

        assertThat(duration).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("터무니없이 멀면 최대 5시간으로 자른다")
    void hasMaximum() {
        Duration duration = StraightLineRideDuration.estimate(
                PANGYO_LAT, PANGYO_LNG, new BigDecimal("35.179600"), new BigDecimal("129.075600"));

        assertThat(duration).isEqualTo(Duration.ofHours(5));
    }
}
