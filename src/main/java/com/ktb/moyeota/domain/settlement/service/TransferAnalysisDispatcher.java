package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.settlement.config.TransferAnalysisConfig;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class TransferAnalysisDispatcher {

    private final TransferAnalysisService transferAnalysisService;
    private final TaskExecutor executor;

    public TransferAnalysisDispatcher(TransferAnalysisService transferAnalysisService,
                                      @Qualifier(TransferAnalysisConfig.TRANSFER_ANALYSIS_EXECUTOR) TaskExecutor executor) {
        this.transferAnalysisService = transferAnalysisService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(TransferAnalysisRequestedEvent request) {
        try {
            executor.execute(() -> analyze(request));
        } catch (TaskRejectedException e) {
            log.warn("[TRANSFER_ANALYSIS_REJECTED] settlementId={} userId={}", request.settlementId(), request.userId());
            transferAnalysisService.rejectBusy(request);
        }
    }

    private void analyze(TransferAnalysisRequestedEvent request) {
        try {
            transferAnalysisService.analyze(request);
        } catch (RuntimeException e) {
            log.error("[TRANSFER_ANALYSIS_FAILED] settlementId={} userId={}",
                    request.settlementId(), request.userId(), e);
        }
    }
}
