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
class TaxiPotRideStartSchedulerTest {

    @Mock
    private TaxiPotService taxiPotService;

    @InjectMocks
    private TaxiPotRideStartScheduler scheduler;

    @Test
    @DisplayName("출발 시각이 지난 팟마다 운행 시작 확인을 요청한다")
    void requestsForEachDuePot() {
        given(taxiPotService.findRideStartDueIds()).willReturn(List.of(30L, 31L));

        scheduler.sweep();

        verify(taxiPotService).requestRideStart(30L);
        verify(taxiPotService).requestRideStart(31L);
    }

    @Test
    @DisplayName("한 팟이 실패해도 나머지 팟은 계속 요청한다")
    void continuesAfterFailure() {
        given(taxiPotService.findRideStartDueIds()).willReturn(List.of(30L, 31L));
        willThrow(new DataIntegrityViolationException("uk_messages_room_client"))
                .given(taxiPotService).requestRideStart(30L);

        scheduler.sweep();

        verify(taxiPotService).requestRideStart(31L);
    }
}
