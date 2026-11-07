package com.ktb.moyeota.domain.carpool.scheduler;

import com.ktb.moyeota.domain.carpool.service.CarpoolRideService;
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
public class CarpoolRideEndScheduler {

    private final CarpoolRideService carpoolRideService;

    @Scheduled(fixedDelayString = "${moyeota.carpool.ride-end-sweep-delay}")
    public void sweep() {
        for (Long carpoolId : carpoolRideService.findRideEndDueIds()) {
            try {
                carpoolRideService.requestRideEnd(carpoolId);
            } catch (RuntimeException e) {
                log.warn("[CARPOOL_RIDE_END_REQUEST_FAILED] carpoolId={}", carpoolId, e);
            }
        }
    }
}
