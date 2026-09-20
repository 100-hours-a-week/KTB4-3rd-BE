package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.time.LocalDateTime;

/**
 * TODO: 댓글 작성자(author) 표시 필드는 별도 설계 예정 — 지금은 포함하지 않는다.
 */
public record CommunityCommentItem(
        Long id,
        String content,
        LocalDateTime createdAt
) {

    public static CommunityCommentItem from(CommunityComment comment) {
        return new CommunityCommentItem(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
