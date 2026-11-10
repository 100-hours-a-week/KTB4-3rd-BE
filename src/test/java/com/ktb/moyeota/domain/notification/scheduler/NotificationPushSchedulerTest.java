package com.ktb.moyeota.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class NotificationPushSchedulerTest {

    private final NotificationPushService pushService = mock(NotificationPushService.class);

    @Test
    @DisplayName("선점한 작업마다 발송 스레드 풀에서 발송한다")
    void processesClaimedJobs() {
        given(pushService.claimDueJobs()).willReturn(List.of(1L, 2L));
        NotificationPushScheduler scheduler = new NotificationPushScheduler(pushService, new SyncTaskExecutor());

        scheduler.sweep();

        verify(pushService).process(1L);
        verify(pushService).process(2L);
    }

    @Test
    @DisplayName("선점할 작업이 없으면 발송하지 않는다")
    void nothingToProcess() {
        given(pushService.claimDueJobs()).willReturn(List.of());
        NotificationPushScheduler scheduler = new NotificationPushScheduler(pushService, new SyncTaskExecutor());

        scheduler.sweep();

        verify(pushService, never()).process(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("한 작업이 실패해도 나머지 작업은 계속 발송한다")
    void continuesAfterFailure() {
        given(pushService.claimDueJobs()).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("fail")).given(pushService).process(1L);
        NotificationPushScheduler scheduler = new NotificationPushScheduler(pushService, new SyncTaskExecutor());

        scheduler.sweep();

        verify(pushService).process(2L);
    }

    @Test
    @DisplayName("스레드 풀이 가득 차서 거절돼도 예외를 던지지 않는다 (선점 만료 후 다음 스윕이 재시도)")
    void rejected() {
        given(pushService.claimDueJobs()).willReturn(List.of(1L));
        TaskExecutor fullExecutor = task -> {
            throw new TaskRejectedException("full");
        };
        NotificationPushScheduler scheduler = new NotificationPushScheduler(pushService, fullExecutor);

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();
        verify(pushService, never()).process(1L);
    }

    @Test
    @DisplayName("선점 단계에서 실패해도 예외를 던지지 않는다")
    void claimFailed() {
        given(pushService.claimDueJobs()).willThrow(new IllegalStateException("db down"));
        NotificationPushScheduler scheduler = new NotificationPushScheduler(pushService, new SyncTaskExecutor());

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();
    }
}
