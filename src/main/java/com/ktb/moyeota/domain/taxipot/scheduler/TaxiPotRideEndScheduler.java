package com.ktb.moyeota.domain.taxipot.scheduler;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxiPotRideEndScheduler {

    private final TaxiPotService taxiPotService;

    @Scheduled(fixedDelayString = "${moyeota.taxi-pot.ride-end-sweep-delay}")
    public void sweep() {
        for (Long taxiPotId : taxiPotService.findRideEndDueIds()) {
            try {
                taxiPotService.requestRideEnd(taxiPotId);
            } catch (RuntimeException e) {
                log.warn("[RIDE_END_REQUEST_FAILED] taxiPotId={}", taxiPotId, e);
            }
        }
    }
}
