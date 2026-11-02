package com.ktb.moyeota.domain.community.message;

import com.ktb.moyeota.domain.community.entity.CommunityPost;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CommunityPostData(
        long postId,
        long authorId,
        String title,
        String content,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime createdAt) {

    public static CommunityPostData from(CommunityPost post) {
        return new CommunityPostData(post.getId(), post.getAuthor().getId(), post.getTitle(), post.getContent(),
                post.getLat(), post.getLng(), post.getCreatedAt());
    }
}
