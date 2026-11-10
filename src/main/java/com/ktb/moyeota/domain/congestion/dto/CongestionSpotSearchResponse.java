package com.ktb.moyeota.domain.congestion.dto;

import java.util.List;

public record CongestionSpotSearchResponse(
        List<CongestionSpotItem> items,
        int limit,
        boolean limitExceeded
) {
}
