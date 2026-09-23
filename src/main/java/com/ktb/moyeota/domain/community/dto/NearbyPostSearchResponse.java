package com.ktb.moyeota.domain.community.dto;

import java.util.List;

public record NearbyPostSearchResponse(
        List<NearbyPostItem> items,
        String nextCursor
) {
}
