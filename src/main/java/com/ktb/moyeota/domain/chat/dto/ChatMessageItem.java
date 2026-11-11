package com.ktb.moyeota.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Sender;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import java.time.LocalDateTime;
import java.time.ZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageItem(
        String id,
        long seq,
        MessageType type,
        Sender sender,
        String content,
        LocalDateTime createdAt
) implements ChatMessageView {

    public static ChatMessageItem text(ChatMessage message, Sender sender, ZoneId zone) {
        return new ChatMessageItem(
                message.getId().toString(),
                message.getSeq(),
                message.getType(),
                sender,
                message.getContent(),
                LocalDateTime.ofInstant(message.getCreatedAt(), zone));
    }
}
