package com.ktb.moyeota.domain.congestion.message;

import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.model.CongestionReport;
import java.util.List;
import java.util.stream.Collectors;

public record CongestionAnalysis(
        CongestionLevel congestionLevel,
        int reportCount,
        List<CongestionCause> causes,
        String summary) {

    public CongestionAnalysis {
        causes = List.copyOf(causes);
    }

    public CongestionReport toReport() {
        return new CongestionReport(congestionLevel, causes.stream()
                .collect(Collectors.toMap(CongestionCause::causeCode, CongestionCause::reportCount, Integer::sum)));
    }
}
