package com.ktb.moyeota.domain.chat.dto;

public record ChatMessageSendRequest(
        String clientMessageId,
        String content
) {
}
