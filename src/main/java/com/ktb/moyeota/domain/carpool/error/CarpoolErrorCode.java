package com.ktb.moyeota.domain.carpool.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CarpoolErrorCode implements ErrorCode {

    HOST_ONLY(HttpStatus.FORBIDDEN, "카풀 등록자만 처리할 수 있습니다"),

    CARPOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카풀입니다");

    private final HttpStatus status;
    private final String message;
}
