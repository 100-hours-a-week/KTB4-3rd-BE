package com.ktb.moyeota.domain.community.message;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.time.LocalDateTime;

public record CommunityCommentData(
        long commentId,
        long authorId,
        String content,
        LocalDateTime createdAt) {

    public static CommunityCommentData from(CommunityComment comment) {
        return new CommunityCommentData(comment.getId(), comment.getAuthor().getId(), comment.getContent(),
                comment.getCreatedAt());
    }
}
