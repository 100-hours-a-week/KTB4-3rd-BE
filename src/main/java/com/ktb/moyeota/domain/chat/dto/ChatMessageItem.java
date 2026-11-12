package com.ktb.moyeota.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Sender;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Settlement;
import com.ktb.moyeota.domain.chat.dto.MessageItem.SystemActor;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import java.time.LocalDateTime;
import java.time.ZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageItem(
        String id,
        long seq,
        MessageType type,
        Sender sender,
        SystemActor joiner,
        SystemActor leaver,
        String content,
        LocalDateTime createdAt,
        Settlement settlement,
        SystemActor transferrer,
        Integer amount
) implements ChatMessageView {

    public static ChatMessageItem text(ChatMessage message, Sender sender, ZoneId zone) {
        return new ChatMessageItem(message.getId().toString(), message.getSeq(), message.getType(), sender,
                null, null, message.getContent(), LocalDateTime.ofInstant(message.getCreatedAt(), zone), null, null, null);
    }

    public static ChatMessageItem system(ChatMessage message, SystemActor actor, Settlement settlement,
            ZoneId zone) {
        return system(message, actor, settlement, null, zone);
    }

    public static ChatMessageItem system(ChatMessage message, SystemActor actor, Settlement settlement,
            Integer transferAmount, ZoneId zone) {
        boolean transferDone = message.getType() == MessageType.SYSTEM_TRANSFER_DONE;
        return new ChatMessageItem(
                message.getId().toString(),
                message.getSeq(),
                message.getType(),
                null,
                message.getType() == MessageType.SYSTEM_JOIN ? actor : null,
                message.getType() == MessageType.SYSTEM_LEAVE ? actor : null,
                message.getContent(),
                LocalDateTime.ofInstant(message.getCreatedAt(), zone),
                message.getType() == MessageType.SYSTEM_TRANSFER_REQUESTED ? settlement : null,
                transferDone ? actor : null,
                transferDone ? transferAmount : null);
    }
}
