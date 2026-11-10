package com.ktb.moyeota.domain.congestion.message;

import com.ktb.moyeota.global.event.InvalidEventException;
import java.time.OffsetDateTime;

public record CongestionAnalysisResultData(
        Long spotId,
        OffsetDateTime windowEnd,
        AnalysisStatus analysisStatus,
        CongestionAnalysis result,
        OffsetDateTime analyzedAt) {

    public CongestionAnalysisResultData {
        if (spotId == null || windowEnd == null || analysisStatus == null) {
            throw new InvalidEventException(
                    "spot_id · window_end · analysis_status 가 모두 있어야 합니다. spot_id=" + spotId);
        }
        if (analysisStatus == AnalysisStatus.SUCCESS && result == null) {
            throw new InvalidEventException("성공 결과에 result 가 없습니다. spot_id=" + spotId);
        }
    }
}
