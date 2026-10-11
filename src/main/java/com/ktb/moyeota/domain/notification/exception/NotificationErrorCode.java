package com.ktb.moyeota.domain.notification.exception;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NotificationErrorCode implements ErrorCode {

    INVALID_TAB(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "탭 값이 올바르지 않아요", "tab"),

    UNSUPPORTED_TAB(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "지원하지 않는 탭이에요", "tab"),

    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "커서 값이 올바르지 않아요", "cursor"),

    INVALID_NOTIFICATION_ID(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "알림 id 값이 올바르지 않아요",
            "max_notification_id"),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "알림을 찾을 수 없어요", null);

    private final HttpStatus status;
    private final String code;
    private final String message;
    private final String field;

    NotificationErrorCode(HttpStatus status, String code, String message, String field) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.field = field;
    }
}
