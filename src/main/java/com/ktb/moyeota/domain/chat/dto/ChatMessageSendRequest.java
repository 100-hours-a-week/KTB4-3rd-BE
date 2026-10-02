package com.ktb.moyeota.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank @Size(max = 36)
        String clientMessageId,

        @NotBlank @Size(max = 500)
        String content
) {
}
