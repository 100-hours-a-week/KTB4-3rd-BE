package com.ktb.moyeota.domain.community.dto;

import java.util.List;

public record CommunityCommentListResponse(
        List<CommunityCommentItem> items,
        Long nextCursor
) {
}
