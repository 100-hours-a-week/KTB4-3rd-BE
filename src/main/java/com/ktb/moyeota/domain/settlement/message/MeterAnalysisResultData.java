package com.ktb.moyeota.domain.settlement.message;

import com.ktb.moyeota.global.event.InvalidEventException;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import java.time.OffsetDateTime;

public record MeterAnalysisResultData(
        Long settlementId,
        OffsetDateTime analysisStartedAt,
        ImageAnalysisStatus analysisStatus,
        String errorCode,
        Integer cost) {

    public MeterAnalysisResultData {
        if (settlementId == null || analysisStartedAt == null || analysisStatus == null) {
            throw new InvalidEventException(
                    "settlement_id · analysis_started_at · analysis_status 가 모두 있어야 합니다. settlement_id="
                            + settlementId);
        }
        if (analysisStatus == ImageAnalysisStatus.READ && cost == null) {
            throw new InvalidEventException("읽음 결과에 cost 가 없습니다. settlement_id=" + settlementId);
        }
        if (analysisStatus != ImageAnalysisStatus.READ && (errorCode == null || errorCode.isBlank())) {
            throw new InvalidEventException("실패 결과에 error_code 가 없습니다. settlement_id=" + settlementId);
        }
    }

    public AnalysisOutcome<Integer> toOutcome() {
        return switch (analysisStatus) {
            case READ -> new AnalysisOutcome.Read<>(cost);
            case UNREADABLE -> new AnalysisOutcome.Unreadable<>(errorCode);
            case SYSTEM_FAILURE -> new AnalysisOutcome.SystemFailure<>(errorCode);
        };
    }
}
