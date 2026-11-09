package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.config.MeterAnalysisProperties;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.repository.MeterAnalysisAttemptProjection;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MeterAnalysisExpiryService {

    static final String ANALYSIS_EXPIRED = "ANALYSIS_EXPIRED";

    private final SettlementRepository settlementRepository;
    private final MeterAnalysisService meterAnalysisService;
    private final MeterAnalysisProperties properties;
    private final Clock clock;

    public List<MeterAnalysisAttemptProjection> findExpired() {
        return settlementRepository.findMeterAnalysisAttemptsStartedBefore(
                MeterAnalysisStatus.ANALYZING, LocalDateTime.now(clock).minus(properties.expireAfter()));
    }

    public void expire(MeterAnalysisAttemptProjection attempt) {
        meterAnalysisService.apply(
                attempt.settlementId(), attempt.startedAt(), new AnalysisOutcome.SystemFailure<>(ANALYSIS_EXPIRED));
    }
}
