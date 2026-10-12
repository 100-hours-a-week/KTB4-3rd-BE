package com.ktb.moyeota.domain.user.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PushTokenErrorCode implements ErrorCode {

    INVALID_PUSH_TOKEN(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해주세요", "token");

    private final HttpStatus status;
    private final String code;
    private final String message;
    private final String field;

    PushTokenErrorCode(HttpStatus status, String code, String message, String field) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.field = field;
    }
}
