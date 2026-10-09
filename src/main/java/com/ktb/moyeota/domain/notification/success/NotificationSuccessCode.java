package com.ktb.moyeota.domain.notification.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements SuccessCode {

    NOTIFICATIONS_FOUND("조회에 성공했습니다"),

    NO_NOTIFICATIONS("알림 내역이 없어요"),

    NOTIFICATION_READ("읽음 처리되었습니다"),

    ALL_NOTIFICATIONS_READ("모두 읽음 처리되었습니다"),

    NO_NOTIFICATIONS_TO_READ("읽음 처리할 알림이 없어요");

    private final String message;
}
