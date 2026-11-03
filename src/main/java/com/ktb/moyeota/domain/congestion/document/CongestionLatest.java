package com.ktb.moyeota.domain.congestion.document;

import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.Instant;
import java.util.List;

public record CongestionLatest(
        CongestionStatus status,
        CongestionLevel congestionLevel,
        List<CauseReport> causes,
        String summary,
        int reportCount,
        Instant calculatedAt,
        Instant validUntil) {

    public CongestionLatest {
        causes = List.copyOf(causes);
    }
}
