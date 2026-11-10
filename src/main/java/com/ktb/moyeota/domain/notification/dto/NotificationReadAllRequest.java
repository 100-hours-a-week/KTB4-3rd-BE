package com.ktb.moyeota.domain.notification.dto;

public record NotificationReadAllRequest(
        Long maxNotificationId
) {

    public boolean hasTarget() {
        return maxNotificationId != null;
    }

    public boolean isValidId() {
        return maxNotificationId == null || maxNotificationId > 0;
    }
}
