package com.ktb.moyeota.domain.carpool.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.carpool.service.CarpoolRequestService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarpoolRequestExpireSchedulerTest {

    @Test
    @DisplayName("한 카풀 처리가 실패해도 다음 카풀은 계속 처리한다")
    void isolatesFailure() {
        CarpoolRequestService service = mock(CarpoolRequestService.class);
        given(service.findExpireDueIds()).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("boom")).given(service).expireDepartedRequests(2L);

        new CarpoolRequestExpireScheduler(service).sweep();

        verify(service).expireDepartedRequests(1L);
        verify(service).expireDepartedRequests(2L);
        verify(service).expireDepartedRequests(3L);
    }
}
