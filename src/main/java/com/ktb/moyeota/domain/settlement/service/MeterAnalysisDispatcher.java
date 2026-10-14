package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.config.MeterAnalysisConfig;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MeterAnalysisDispatcher {

    private final MeterAnalysisService meterAnalysisService;
    private final TaskExecutor executor;

    public MeterAnalysisDispatcher(MeterAnalysisService meterAnalysisService,
                                   @Qualifier(MeterAnalysisConfig.METER_ANALYSIS_EXECUTOR) TaskExecutor executor) {
        this.meterAnalysisService = meterAnalysisService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(MeterAnalysisRequestedEvent request) {
        try {
            executor.execute(() -> analyze(request));
        } catch (TaskRejectedException e) {
            log.warn("[METER_ANALYSIS_REJECTED] settlementId={}", request.settlementId());
            meterAnalysisService.rejectBusy(request);
        }
    }

    private void analyze(MeterAnalysisRequestedEvent request) {
        try {
            meterAnalysisService.analyze(request);
        } catch (RuntimeException e) {
            log.error("[METER_ANALYSIS_FAILED] settlementId={}", request.settlementId(), e);
        }
    }
}
