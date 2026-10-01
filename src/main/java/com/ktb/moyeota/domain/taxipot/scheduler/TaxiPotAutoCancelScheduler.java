package com.ktb.moyeota.domain.taxipot.scheduler;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxiPotAutoCancelScheduler {

    private final TaxiPotService taxiPotService;

    @Scheduled(fixedDelayString = "${moyeota.taxi-pot.auto-cancel-sweep-delay}")
    public void sweep() {
        for (Long taxiPotId : taxiPotService.findAutoCancelDueIds()) {
            try {
                taxiPotService.autoCancel(taxiPotId);
            } catch (RuntimeException e) {
                log.warn("[AUTO_CANCEL_FAILED] taxiPotId={}", taxiPotId, e);
            }
        }
    }
}
