package com.ktb.moyeota.domain.community.dto;

import java.util.List;

public record MapPinSearchResponse(
        List<MapPinItem> items,
        int limit,
        boolean limitExceeded
) {
}
