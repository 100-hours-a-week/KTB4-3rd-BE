package com.ktb.moyeota.domain.notification.dto;

import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Long eventId,
        Long targetChatRoomId,
        Long targetPostId,
        String content,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getEventId(),
                notification.getTargetChatRoom() == null ? null : notification.getTargetChatRoom().getId(),
                notification.getTargetPost() == null ? null : notification.getTargetPost().getId(),
                notification.getContent(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
