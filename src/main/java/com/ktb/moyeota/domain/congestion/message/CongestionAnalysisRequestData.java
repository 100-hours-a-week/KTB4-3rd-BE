package com.ktb.moyeota.domain.congestion.message;

import java.time.OffsetDateTime;
import java.util.List;

public record CongestionAnalysisRequestData(Long spotId, OffsetDateTime windowEnd, List<PostBundle> postBundles) {

    public CongestionAnalysisRequestData {
        postBundles = List.copyOf(postBundles);
    }
}
