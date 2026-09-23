package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.time.LocalDateTime;

public record CommunityCommentItem(
        Long id,
        String nickname,
        String content,
        LocalDateTime createdAt
) {

    public static CommunityCommentItem from(CommunityComment comment) {
        return new CommunityCommentItem(
                comment.getId(),
                comment.getAuthor().getNickname(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
