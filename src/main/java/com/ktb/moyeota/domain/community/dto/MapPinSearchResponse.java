package com.ktb.moyeota.domain.community.dto;

import java.util.List;

/** 합산 결과가 500건 초과면 items는 빈 배열 + limitExceeded = true. */
public record MapPinSearchResponse(
        List<MapPinItem> items,
        boolean limitExceeded
) {
}
