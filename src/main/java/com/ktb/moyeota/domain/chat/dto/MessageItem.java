package com.ktb.moyeota.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
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

    public record Sender(
            Long id,
            @JsonInclude(JsonInclude.Include.NON_NULL) String name,
            @JsonInclude(JsonInclude.Include.NON_NULL) String nickname,
            String profileImageUrl) {
    }

    public record SystemActor(Long id, String name) {
    }

    public static MessageItem from(Message message) {
        return switch (message.getMessageType()) {
            case TEXT -> text(message);
            case SYSTEM_JOIN -> join(message);
            case SYSTEM_LEAVE -> leave(message);
            case SYSTEM_RIDE_START_REQUESTED -> rideStartRequested(message);
            case SYSTEM_RIDE_END_REQUESTED -> rideEndRequested(message);
            case SYSTEM_RIDE_STARTED -> rideStarted(message);
            case SYSTEM_RIDE_ENDED -> rideEnded(message);
        };
    }

    private static MessageItem text(Message message) {
        return new MessageItem(
                message.getId(),
                message.getMessageType(),
                sender(message),
                null,
                null,
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private static Sender sender(Message message) {
        User sender = message.getSender();
        if (message.getChatRoom().getCompanion().getKind() == CompanionKind.TAXI_POT) {
            return new Sender(sender.getId(), sender.getName(), null, sender.getProfileImageUrl());
        }
        return new Sender(sender.getId(), null, sender.getNickname(), sender.getProfileImageUrl());
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

    private static MessageItem rideEndRequested(Message message) {
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

    private static MessageItem rideStarted(Message message) {
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

    private static MessageItem rideEnded(Message message) {
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
