package com.ktb.moyeota.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),

    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "요청 값 검증에 실패했습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 API입니다."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
