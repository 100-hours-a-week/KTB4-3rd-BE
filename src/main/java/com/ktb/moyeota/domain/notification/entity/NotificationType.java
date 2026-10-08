package com.ktb.moyeota.domain.notification.entity;

public enum NotificationType {
    CARPOOL_REQUEST_RECEIVED,
    CARPOOL_REQUEST_ACCEPTED,
    SETTLEMENT_REQUESTED,
    MATCHING_COMPLETED,
    COMMENT_CREATED;

    // 이동 대상이 게시글이면 true(댓글 알림), 채팅방이면 false(그 외 전부)
    public boolean targetsPost() {
        return this == COMMENT_CREATED;
    }
}
