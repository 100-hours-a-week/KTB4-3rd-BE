package com.ktb.moyeota.domain.taxipot.scheduler;

import com.ktb.moyeota.domain.taxipot.service.DepartedMatchRequestService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.taxi-pot.matching", name = "mode", havingValue = "kafka")
public class DepartedMatchRequestScheduler {

    private final DepartedMatchRequestService departedMatchRequestService;

    @Scheduled(fixedDelayString = "${moyeota.taxi-pot.matching.sweep-delay:5s}")
    public void sweep() {
        try {
            int failed = departedMatchRequestService.failDeparted();
            if (failed > 0) {
                log.info("[MATCH_REQUEST_DEPARTED] failed={}", failed);
            }
        } catch (RuntimeException e) {
            log.warn("[MATCH_REQUEST_DEPARTED_FAILED]", e);
        }
    }
}
