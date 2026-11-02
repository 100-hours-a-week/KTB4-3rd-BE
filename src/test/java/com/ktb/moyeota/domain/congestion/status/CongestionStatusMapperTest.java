package com.ktb.moyeota.domain.congestion.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.config.CongestionStatusProperties;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.model.CongestionReport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CongestionStatusMapperTest {

    private final CongestionStatusMapper mapper = new CongestionStatusMapper(new CongestionStatusProperties(3, 3));

    @ParameterizedTest(name = "원인 제보가 없으면 {0} 은 {1}")
    @CsvSource({
            "LOW, SMOOTH",
            "MEDIUM, CONGESTED",
            "HIGH, CROWDED"
    })
    void mapsLevel(CongestionLevel level, CongestionStatus expected) {
        assertThat(mapper.statusOf(report(level, Map.of()))).contains(expected);
    }

    @Test
    @DisplayName("단계를 판단할 제보가 없으면 상태를 비운다")
    void unknownLevelHasNoStatus() {
        assertThat(mapper.statusOf(report(CongestionLevel.UNKNOWN, Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("사고 제보자가 3명이면 붐빔보다 사고가 먼저다")
    void accidentBeatsCrowded() {
        CongestionReport report = report(CongestionLevel.HIGH, Map.of(CauseCode.ACCIDENT, 3));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.ACCIDENT);
    }

    @Test
    @DisplayName("사고 제보자가 2명이면 사고로 확정하지 않는다")
    void twoAccidentReportersAreNotAccident() {
        CongestionReport report = report(CongestionLevel.LOW, Map.of(CauseCode.ACCIDENT, 2));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.SMOOTH);
    }

    @Test
    @DisplayName("붐빔이면 지연 제보가 기준을 넘어도 붐빔이다")
    void crowdedBeatsDelayed() {
        CongestionReport report = report(CongestionLevel.HIGH, Map.of(CauseCode.DELAY, 3));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.CROWDED);
    }

    @ParameterizedTest(name = "{0} 제보자가 3명이면 혼잡보다 지연이 먼저다")
    @CsvSource({"DELAY", "SERVICE_DISRUPTION"})
    void delayBeatsCongested(CauseCode cause) {
        CongestionReport report = report(CongestionLevel.MEDIUM, Map.of(cause, 3));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.DELAYED);
    }

    @Test
    @DisplayName("지연과 운행 중단은 더하지 않고 하나씩 기준과 비교한다")
    void delayCausesAreNotSummed() {
        CongestionReport report = report(CongestionLevel.LOW,
                Map.of(CauseCode.DELAY, 2, CauseCode.SERVICE_DISRUPTION, 2));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.SMOOTH);
    }

    @Test
    @DisplayName("단계를 몰라도 지연 제보가 기준을 넘으면 지연이다")
    void delayWithoutLevel() {
        CongestionReport report = report(CongestionLevel.UNKNOWN, Map.of(CauseCode.DELAY, 3));

        assertThat(mapper.statusOf(report)).contains(CongestionStatus.DELAYED);
    }

    @Test
    @DisplayName("지연 기준 수를 설정으로 바꿀 수 있다")
    void delayThresholdFromProperties() {
        CongestionStatusMapper lowThreshold = new CongestionStatusMapper(new CongestionStatusProperties(3, 2));
        CongestionReport report = report(CongestionLevel.LOW, Map.of(CauseCode.DELAY, 2));

        assertThat(lowThreshold.statusOf(report)).contains(CongestionStatus.DELAYED);
    }

    @Test
    @DisplayName("사고 기준 수를 설정으로 바꿀 수 있다")
    void accidentThresholdFromProperties() {
        CongestionStatusMapper lowThreshold = new CongestionStatusMapper(new CongestionStatusProperties(2, 3));
        CongestionReport report = report(CongestionLevel.LOW, Map.of(CauseCode.ACCIDENT, 2));

        assertThat(lowThreshold.statusOf(report)).contains(CongestionStatus.ACCIDENT);
    }

    private static CongestionReport report(CongestionLevel level, Map<CauseCode, Integer> causeReporters) {
        return new CongestionReport(level, causeReporters);
    }
}
