package com.ktb.moyeota.domain.carpool.scheduler;

import com.ktb.moyeota.domain.carpool.service.CarpoolRideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CarpoolAutoCancelScheduler {

    private final CarpoolRideService carpoolRideService;

    @Scheduled(fixedDelayString = "${moyeota.carpool.auto-cancel-sweep-delay}")
    public void sweep() {
        for (Long carpoolId : carpoolRideService.findAutoCancelDueIds()) {
            try {
                carpoolRideService.autoCancel(carpoolId);
            } catch (RuntimeException e) {
                log.warn("[CARPOOL_AUTO_CANCEL_FAILED] carpoolId={}", carpoolId, e);
            }
        }
    }
}
