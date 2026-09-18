package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /community-posts/{post_id}/comments 요청. */
public record CommunityCommentCreateRequest(

        @NotBlank
        @Size(max = 500)
        String content
) {
}
