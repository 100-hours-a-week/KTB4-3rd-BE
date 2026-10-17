package com.ktb.moyeota.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InternalChatMessageSendRequest(
        @NotNull
        Long userId,

        @NotBlank @Size(max = 36)
        String clientMessageId,

        @NotBlank @Size(max = 500)
        String content
) {

    public ChatMessageSendRequest toSendRequest() {
        return new ChatMessageSendRequest(clientMessageId, content);
    }
}
