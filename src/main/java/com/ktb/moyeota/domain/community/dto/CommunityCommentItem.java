package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.time.LocalDateTime;

public record CommunityCommentItem(
        Long id,
        Author author,
        String content,
        LocalDateTime createdAt
) {

    public record Author(String nickname, String profileImageUrl) {
    }

    public static CommunityCommentItem from(CommunityComment comment, String authorProfileImageUrl) {
        return new CommunityCommentItem(
                comment.getId(),
                new Author(comment.getAuthor().getNickname(), authorProfileImageUrl),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
