package com.ktb.moyeota.domain.notification.message;

import com.ktb.moyeota.domain.notification.entity.NotificationType;

public enum NotificationPushChannel {

    PUSH(NotificationPushMessages.PUSH_TOPIC, NotificationPushMessages.PUSH_REQUESTED),
    BULK(NotificationPushMessages.BULK_PUSH_TOPIC, NotificationPushMessages.BULK_PUSH_REQUESTED);

    private final String topic;
    private final String eventType;

    NotificationPushChannel(String topic, String eventType) {
        this.topic = topic;
        this.eventType = eventType;
    }

    public static NotificationPushChannel of(NotificationType type) {
        return type == NotificationType.SETTLEMENT_COMPLETION_REMINDER ? BULK : PUSH;
    }

    public String topic() {
        return topic;
    }

    public String eventType() {
        return eventType;
    }
}
