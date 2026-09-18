package com.ktb.moyeota.domain.community.dto;

import java.util.List;

/** GET /community-posts/{post_id}/comments 응답. id 내림차순, 10건 단위 커서 페이지네이션. */
public record CommunityCommentListResponse(
        List<CommunityCommentItem> items,
        Long nextCursor
) {
}
