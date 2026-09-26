package com.ktb.moyeota.domain.chat.dto;

public record ChatMessageSendRequest(
        Long clientMessageId,
        String content
) {
}
