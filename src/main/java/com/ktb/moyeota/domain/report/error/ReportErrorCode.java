package com.ktb.moyeota.domain.report.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReportErrorCode implements ErrorCode {

    REASON_TEXT_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 입력해주세요", "VALIDATION_ERROR", "reason_text"),

    DUPLICATE_MESSAGE_REPORT(HttpStatus.CONFLICT, "이미 신고한 메시지입니다", "DUPLICATE_REPORT", "reported_message_id"),

    DUPLICATE_USER_REPORT(HttpStatus.CONFLICT, "이미 신고한 유저입니다", "DUPLICATE_REPORT", "reported_user_id"),

    REPORT_TARGET_INVALID(HttpStatus.BAD_REQUEST, "신고 대상을 찾을 수 없습니다"),

    REPORTED_USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 사용자입니다");

    private final HttpStatus status;
    private final String message;
    private final String code;
    private final String field;

    ReportErrorCode(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    ReportErrorCode(HttpStatus status, String message, String code, String field) {
        this.status = status;
        this.message = message;
        this.code = code;
        this.field = field;
    }

    @Override
    public String getCode() {
        return code != null ? code : name();
    }

    @Override
    public String getField() {
        return field;
    }
}
