package com.ktb.moyeota.domain.taxipot.scheduler;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class TaxiPotRideStartScheduler {

    private final TaxiPotService taxiPotService;

    @Scheduled(fixedDelayString = "${moyeota.taxi-pot.ride-start-sweep-delay}")
    public void sweep() {
        for (Long taxiPotId : taxiPotService.findRideStartDueIds()) {
            try {
                taxiPotService.requestRideStart(taxiPotId);
            } catch (RuntimeException e) {
                log.warn("[RIDE_START_REQUEST_FAILED] taxiPotId={}", taxiPotId, e);
            }
        }
    }
}
