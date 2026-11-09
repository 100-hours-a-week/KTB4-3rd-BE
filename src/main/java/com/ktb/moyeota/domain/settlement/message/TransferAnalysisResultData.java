package com.ktb.moyeota.domain.settlement.message;

import com.ktb.moyeota.global.event.InvalidEventException;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;

public record TransferAnalysisResultData(
        Long settlementId,
        Long userId,
        Integer attempt,
        ImageAnalysisStatus analysisStatus,
        String errorCode,
        Integer amountCost) {

    public TransferAnalysisResultData {
        if (settlementId == null || userId == null || attempt == null || analysisStatus == null) {
            throw new InvalidEventException(
                    "settlement_id · user_id · attempt · analysis_status 가 모두 있어야 합니다. settlement_id="
                            + settlementId + " user_id=" + userId);
        }
        if (analysisStatus == ImageAnalysisStatus.READ && amountCost == null) {
            throw new InvalidEventException(
                    "읽음 결과에 amount_cost 가 없습니다. settlement_id=" + settlementId + " user_id=" + userId);
        }
        if (analysisStatus != ImageAnalysisStatus.READ && (errorCode == null || errorCode.isBlank())) {
            throw new InvalidEventException(
                    "실패 결과에 error_code 가 없습니다. settlement_id=" + settlementId + " user_id=" + userId);
        }
    }

    public AnalysisOutcome<Integer> toOutcome() {
        return switch (analysisStatus) {
            case READ -> new AnalysisOutcome.Read<>(amountCost);
            case UNREADABLE -> new AnalysisOutcome.Unreadable<>(errorCode);
            case SYSTEM_FAILURE -> new AnalysisOutcome.SystemFailure<>(errorCode);
        };
    }
}
