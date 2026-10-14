package com.ktb.moyeota.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class MeterAnalysisDispatcherTest {

    private static final MeterAnalysisRequestedEvent REQUEST =
            new MeterAnalysisRequestedEvent(77L, "meter/a.jpg", LocalDateTime.of(2026, 9, 7, 9, 0, 3));

    private final RecordingMeterAnalysisService meterAnalysisService = new RecordingMeterAnalysisService();

    @Test
    @DisplayName("분석 전용 스레드 풀에 넘겨 분석한다")
    void analyzesOnExecutor() {
        new MeterAnalysisDispatcher(meterAnalysisService, new SyncTaskExecutor()).dispatch(REQUEST);

        assertThat(meterAnalysisService.analyzed).containsExactly(REQUEST);
        assertThat(meterAnalysisService.rejected).isEmpty();
    }

    @Test
    @DisplayName("대기열이 가득 차 거절되면 시스템 문제로 처리한다")
    void rejectsWhenBusy() {
        new MeterAnalysisDispatcher(meterAnalysisService, task -> {
            throw new TaskRejectedException("queue full");
        }).dispatch(REQUEST);

        assertThat(meterAnalysisService.analyzed).isEmpty();
        assertThat(meterAnalysisService.rejected).containsExactly(REQUEST);
    }

    private static class RecordingMeterAnalysisService extends MeterAnalysisService {

        private final List<MeterAnalysisRequestedEvent> analyzed = new ArrayList<>();
        private final List<MeterAnalysisRequestedEvent> rejected = new ArrayList<>();

        RecordingMeterAnalysisService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public void analyze(MeterAnalysisRequestedEvent request) {
            analyzed.add(request);
        }

        @Override
        public void rejectBusy(MeterAnalysisRequestedEvent request) {
            rejected.add(request);
        }
    }
}
