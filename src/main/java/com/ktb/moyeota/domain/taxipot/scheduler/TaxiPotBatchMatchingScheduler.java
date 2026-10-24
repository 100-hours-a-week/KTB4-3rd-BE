package com.ktb.moyeota.domain.taxipot.scheduler;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotBatchMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.taxi-pot.matching", name = "mode", havingValue = "batch")
public class TaxiPotBatchMatchingScheduler {

    private final TaxiPotBatchMatchingService taxiPotBatchMatchingService;

    @Scheduled(fixedDelayString = "${moyeota.taxi-pot.matching.sweep-delay:30s}")
    public void sweep() {
        try {
            taxiPotBatchMatchingService.runOnce();
        } catch (RuntimeException e) {
            log.warn("[BATCH_MATCHING_FAILED]", e);
        }
    }
}
