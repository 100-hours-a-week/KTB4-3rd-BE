package com.ktb.moyeota.domain.congestion.batch;

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
public class CongestionWindowCloseScheduler {

    private final CongestionWindowCloser windowCloser;

    @Scheduled(fixedDelayString = "${moyeota.congestion.batch.check-delay:10s}")
    public void close() {
        try {
            windowCloser.closeEndedWindows();
        } catch (RuntimeException e) {
            log.warn("[CONGESTION_WINDOW_CLOSE_FAILED]", e);
        }
    }
}
