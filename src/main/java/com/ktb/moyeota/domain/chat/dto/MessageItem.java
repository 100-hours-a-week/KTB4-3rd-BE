package com.ktb.moyeota.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageItem(
        Long id,
        MessageType type,
        Sender sender,
        SystemActor joiner,
        SystemActor leaver,
        String content,
        LocalDateTime createdAt
) {

    public record Sender(Long id, String nickname, String profileImageUrl) {
    }

    public record SystemActor(Long id, String name) {
    }

    public static MessageItem from(Message message) {
        return switch (message.getMessageType()) {
            case TEXT -> text(message);
            case SYSTEM_JOIN -> join(message);
            case SYSTEM_LEAVE -> leave(message);
            case SYSTEM_RIDE_START_REQUESTED -> rideStartRequested(message);
        };
    }

    private static MessageItem text(Message message) {
        User sender = message.getSender();
        return new MessageItem(
                message.getId(),
                message.getMessageType(),
                new Sender(sender.getId(), sender.getNickname(), sender.getProfileImageUrl()),
                null,
                null,
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private static MessageItem join(Message message) {
        User joiner = message.getSender(); // sender 컬럼을 입장한 사람으로 재활용
        return new MessageItem(
                message.getId(),
                message.getMessageType(),
                null,
                new SystemActor(joiner.getId(), joiner.getName()),
                null,
                null,
                message.getCreatedAt()
        );
    }

    private static MessageItem leave(Message message) {
        User leaver = message.getSender(); // sender 컬럼을 퇴장한 사람으로 재활용
        return new MessageItem(
                message.getId(),
                message.getMessageType(),
                null,
                null,
                new SystemActor(leaver.getId(), leaver.getName()),
                null,
                message.getCreatedAt()
        );
    }

    private static MessageItem rideStartRequested(Message message) {
        return new MessageItem(
                message.getId(),
                message.getMessageType(),
                null,
                null,
                null,
                null,
                message.getCreatedAt()
        );
    }
}
