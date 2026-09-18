package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.repository.CommunityCommentProjection;
import java.time.LocalDateTime;

public record CommunityCommentItem(
        Long id,
        Long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt
) {

    public static CommunityCommentItem from(CommunityCommentProjection projection) {
        return new CommunityCommentItem(
                projection.getId(),
                projection.getAuthorId(),
                projection.getAuthorNickname(),
                projection.getContent(),
                projection.getCreatedAt()
        );
    }
}
