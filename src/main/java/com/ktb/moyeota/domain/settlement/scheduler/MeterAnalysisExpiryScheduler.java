package com.ktb.moyeota.domain.settlement.scheduler;

import com.ktb.moyeota.domain.settlement.repository.MeterAnalysisAttemptProjection;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisExpiryService;
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
public class MeterAnalysisExpiryScheduler {

    private final MeterAnalysisExpiryService meterAnalysisExpiryService;

    @Scheduled(fixedDelayString = "${moyeota.settlement.meter-analysis.expire-sweep-delay}")
    public void sweep() {
        for (MeterAnalysisAttemptProjection attempt : meterAnalysisExpiryService.findExpired()) {
            try {
                meterAnalysisExpiryService.expire(attempt);
            } catch (RuntimeException e) {
                log.warn("[METER_ANALYSIS_EXPIRE_FAILED] settlementId={}", attempt.settlementId(), e);
            }
        }
    }
}
