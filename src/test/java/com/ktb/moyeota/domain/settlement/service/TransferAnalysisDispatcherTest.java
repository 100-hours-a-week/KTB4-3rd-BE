package com.ktb.moyeota.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class TransferAnalysisDispatcherTest {

    private static final TransferAnalysisRequestedEvent REQUEST = new TransferAnalysisRequestedEvent(
            77L, 43L, "transfer/a.jpg", 1, LocalDateTime.of(2026, 9, 7, 9, 10, 3));

    private final RecordingTransferAnalysisService transferAnalysisService = new RecordingTransferAnalysisService();

    @Test
    @DisplayName("분석 전용 스레드 풀에 넘겨 분석한다")
    void analyzesOnExecutor() {
        new TransferAnalysisDispatcher(transferAnalysisService, new SyncTaskExecutor()).dispatch(REQUEST);

        assertThat(transferAnalysisService.analyzed).containsExactly(REQUEST);
        assertThat(transferAnalysisService.rejected).isEmpty();
    }

    @Test
    @DisplayName("대기열이 가득 차 거절되면 시스템 문제로 처리한다")
    void rejectsWhenBusy() {
        new TransferAnalysisDispatcher(transferAnalysisService, task -> {
            throw new TaskRejectedException("queue full");
        }).dispatch(REQUEST);

        assertThat(transferAnalysisService.analyzed).isEmpty();
        assertThat(transferAnalysisService.rejected).containsExactly(REQUEST);
    }

    private static class RecordingTransferAnalysisService extends TransferAnalysisService {

        private final List<TransferAnalysisRequestedEvent> analyzed = new ArrayList<>();
        private final List<TransferAnalysisRequestedEvent> rejected = new ArrayList<>();

        RecordingTransferAnalysisService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public void analyze(TransferAnalysisRequestedEvent request) {
            analyzed.add(request);
        }

        @Override
        public void rejectBusy(TransferAnalysisRequestedEvent request) {
            rejected.add(request);
        }
    }
}
