package com.ktb.moyeota.domain.community.dto;

import com.ktb.moyeota.domain.community.entity.CommunityPost;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** GET /community-posts/{post_id} 응답. 댓글 목록은 포함하지 않는다(별도 API로 분리). */
public record CommunityPostDetailResponse(
        Long id,
        Long authorId,
        String title,
        String content,
        BigDecimal lat,
        BigDecimal lng,
        Integer commentCount,
        LocalDateTime createdAt
) {

    public static CommunityPostDetailResponse from(CommunityPost post) {
        return new CommunityPostDetailResponse(
                post.getId(),
                post.getAuthor().getId(),
                post.getTitle(),
                post.getContent(),
                post.getLat(),
                post.getLng(),
                post.getCommentCount(),
                post.getCreatedAt()
        );
    }
}
