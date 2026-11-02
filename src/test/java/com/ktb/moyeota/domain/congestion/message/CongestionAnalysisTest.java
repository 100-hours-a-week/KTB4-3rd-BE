package com.ktb.moyeota.domain.congestion.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.model.CongestionReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CongestionAnalysisTest {

    @Test
    @DisplayName("원인 목록을 원인별 제보자 수로 바꾼다")
    void toReport() {
        CongestionAnalysis analysis = new CongestionAnalysis(CongestionLevel.MEDIUM, 4,
                List.of(new CongestionCause(CauseCode.ACCIDENT, 3), new CongestionCause(CauseCode.DELAY, 1)), "요약");

        CongestionReport report = analysis.toReport();

        assertThat(report).isEqualTo(new CongestionReport(CongestionLevel.MEDIUM,
                Map.of(CauseCode.ACCIDENT, 3, CauseCode.DELAY, 1)));
    }

    @Test
    @DisplayName("모르는 원인 코드 여러 개가 UNKNOWN 으로 읽히면 제보자 수를 더한다")
    void mergesSameCause() {
        CongestionAnalysis analysis = new CongestionAnalysis(CongestionLevel.LOW, 3,
                List.of(new CongestionCause(CauseCode.UNKNOWN, 1), new CongestionCause(CauseCode.UNKNOWN, 2)), "요약");

        CongestionReport report = analysis.toReport();

        assertThat(report.reportersOf(CauseCode.UNKNOWN)).isEqualTo(3);
        assertThat(report.reportersOf(CauseCode.ACCIDENT)).isZero();
    }
}
