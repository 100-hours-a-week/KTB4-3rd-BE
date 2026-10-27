package com.ktb.moyeota.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.service.SettlementReminderService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCompletionReminderSchedulerTest {

    private final SettlementReminderService reminderService = mock(SettlementReminderService.class);
    private final SettlementCompletionReminderScheduler scheduler =
            new SettlementCompletionReminderScheduler(reminderService);

    @Test
    @DisplayName("REQUESTED로 남은 정산마다 리마인드를 보낸다")
    void remindsEachSettlement() {
        given(reminderService.findSettlementIdsToRemind()).willReturn(List.of(1L, 2L));

        scheduler.remind();

        verify(reminderService).remind(1L);
        verify(reminderService).remind(2L);
    }

    @Test
    @DisplayName("한 정산이 실패해도 나머지 정산은 계속 보낸다")
    void continuesAfterFailure() {
        given(reminderService.findSettlementIdsToRemind()).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("fail")).given(reminderService).remind(1L);

        assertThatCode(scheduler::remind).doesNotThrowAnyException();

        verify(reminderService).remind(2L);
    }

    @Test
    @DisplayName("대상 조회가 실패하면 아무것도 보내지 않는다")
    void stopsWhenLookupFails() {
        given(reminderService.findSettlementIdsToRemind()).willThrow(new IllegalStateException("fail"));

        assertThatCode(scheduler::remind).doesNotThrowAnyException();

        verify(reminderService, never()).remind(anyLong());
    }
}
