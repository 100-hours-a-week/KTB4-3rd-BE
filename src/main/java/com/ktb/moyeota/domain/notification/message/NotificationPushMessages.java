package com.ktb.moyeota.domain.notification.message;

public final class NotificationPushMessages {

    public static final String PUSH_TOPIC = "notification.push.request";
    public static final String BULK_PUSH_TOPIC = "notification.bulk-push.request";
    public static final int SCHEMA_VERSION = 1;

    public static final String PUSH_REQUESTED = "notification.push.requested";
    public static final String BULK_PUSH_REQUESTED = "notification.bulk-push.requested";

    private NotificationPushMessages() {
    }
}
