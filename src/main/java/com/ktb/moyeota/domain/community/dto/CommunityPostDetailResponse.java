package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.entity.CommunityPost;
import java.time.LocalDateTime;

public record CommunityPostDetailResponse(
        Long id,
        String title,
        String content,
        Author author,
        Integer commentCount,
        LocalDateTime createdAt
) {

    public record Author(String nickname, String profileImageUrl) {
    }

    public static CommunityPostDetailResponse from(CommunityPost post, String authorProfileImageUrl) {
        return new CommunityPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new Author(post.getAuthor().getNickname(), authorProfileImageUrl),
                post.getCommentCount(),
                post.getCreatedAt()
        );
    }
}
