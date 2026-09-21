package com.ktb.moyeota.domain.community.dto;

import java.time.LocalDateTime;

public record CommunityCommentCreateResponse(
        Long id,
        Author author,
        String content,
        LocalDateTime createdAt,
        Integer commentCount
) {

    public record Author(String nickname) {
    }
}
