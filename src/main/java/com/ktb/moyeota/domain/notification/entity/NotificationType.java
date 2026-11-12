package com.ktb.moyeota.domain.notification.entity;

public enum NotificationType {
    CARPOOL_REQUEST_RECEIVED,
    CARPOOL_REQUEST_ACCEPTED,
    SETTLEMENT_REQUESTED,
    MATCHING_COMPLETED,
    COMMENT_CREATED,
    SETTLEMENT_COMPLETION_REMINDER,
    CARPOOL_REQUEST_REJECTED,
    CARPOOL_REQUEST_EXPIRED;

    public boolean targetsPost() {
        return this == COMMENT_CREATED;
    }

    /** 눌러도 이동할 화면이 없는 알림 (target_chat_room_id, target_post_id 모두 NULL) */
    public boolean hasNoTarget() {
        return this == CARPOOL_REQUEST_REJECTED || this == CARPOOL_REQUEST_EXPIRED;
    }
}
