package com.ktb.moyeota.domain.taxipot.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TaxiPotRideEndSchedulerTest {

    @Mock
    private TaxiPotService taxiPotService;

    @InjectMocks
    private TaxiPotRideEndScheduler scheduler;

    @Test
    @DisplayName("도착 예정 시각이 지난 팟마다 운행 종료 확인을 요청한다")
    void requestsForEachDuePot() {
        given(taxiPotService.findRideEndDueIds()).willReturn(List.of(30L, 31L));

        scheduler.sweep();

        verify(taxiPotService).requestRideEnd(30L);
        verify(taxiPotService).requestRideEnd(31L);
    }

    @Test
    @DisplayName("한 팟이 실패해도 나머지 팟은 계속 요청한다")
    void continuesAfterFailure() {
        given(taxiPotService.findRideEndDueIds()).willReturn(List.of(30L, 31L));
        willThrow(new DataIntegrityViolationException("uk_messages_room_client"))
                .given(taxiPotService).requestRideEnd(30L);

        scheduler.sweep();

        verify(taxiPotService).requestRideEnd(31L);
    }
}
