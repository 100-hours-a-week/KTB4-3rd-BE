package com.ktb.moyeota.domain.notification.entity;

public enum NotificationType {
    CARPOOL_REQUEST_RECEIVED,
    CARPOOL_REQUEST_ACCEPTED,
    SETTLEMENT_REQUESTED,
    MATCHING_COMPLETED,
    COMMENT_CREATED,
    SETTLEMENT_COMPLETION_REMINDER;

    public boolean targetsPost() {
        return this == COMMENT_CREATED;
    }
}
