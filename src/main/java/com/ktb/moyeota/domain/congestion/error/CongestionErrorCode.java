package com.ktb.moyeota.domain.congestion.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CongestionErrorCode implements ErrorCode {

    SPOT_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "정보를 불러오지 못했어요");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CongestionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
