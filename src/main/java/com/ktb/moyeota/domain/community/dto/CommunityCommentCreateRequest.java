package com.ktb.moyeota.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityCommentCreateRequest(

        @NotBlank
        @Size(max = 280)
        String content
) {
}
