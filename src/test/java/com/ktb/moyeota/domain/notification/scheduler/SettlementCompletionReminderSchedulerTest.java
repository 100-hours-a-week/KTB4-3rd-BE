package com.ktb.moyeota.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.service.SettlementReminderService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCompletionReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime FIRED_AT = LocalDateTime.of(2026, 10, 28, 21, 0, 1, 300_000_000);
    private static final LocalDateTime ROUND = LocalDateTime.of(2026, 10, 28, 21, 0, 0);

    private final SettlementReminderService reminderService = mock(SettlementReminderService.class);

    @Test
    @DisplayName("REQUESTED로 남은 정산을 묶음 크기만큼 나눠 실행 시각을 정시로 자른 회차로 보낸다")
    void remindsInChunks() {
        given(reminderService.findSettlementIdsToRemind()).willReturn(List.of(1L, 2L, 3L));

        scheduler(2).remind();

        verify(reminderService).remindAll(List.of(1L, 2L), ROUND);
        verify(reminderService).remindAll(List.of(3L), ROUND);
    }

    @Test
    @DisplayName("묶음이 실패하면 그 묶음의 정산을 하나씩 다시 보내고, 한 정산이 실패해도 나머지는 계속 보낸다")
    void fallsBackToEachSettlement() {
        given(reminderService.findSettlementIdsToRemind()).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("fail")).given(reminderService).remindAll(List.of(1L, 2L), ROUND);
        willThrow(new IllegalStateException("fail")).given(reminderService).remind(1L, ROUND);

        assertThatCode(scheduler(2)::remind).doesNotThrowAnyException();

        verify(reminderService).remind(2L, ROUND);
        verify(reminderService).remindAll(List.of(3L), ROUND);
        verify(reminderService, never()).remind(3L, ROUND);
    }

    @Test
    @DisplayName("대상 조회가 실패하면 아무것도 보내지 않는다")
    void stopsWhenLookupFails() {
        given(reminderService.findSettlementIdsToRemind()).willThrow(new IllegalStateException("fail"));

        assertThatCode(scheduler(2)::remind).doesNotThrowAnyException();

        verify(reminderService, never()).remindAll(anyList(), any());
        verify(reminderService, never()).remind(anyLong(), any());
    }

    private SettlementCompletionReminderScheduler scheduler(int chunkSize) {
        return new SettlementCompletionReminderScheduler(
                reminderService, Clock.fixed(FIRED_AT.atZone(SEOUL).toInstant(), SEOUL), chunkSize);
    }
}
