package com.ktb.moyeota.domain.taxipot.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaxiPotErrorCode implements ErrorCode {

    HOST_ONLY(HttpStatus.FORBIDDEN, "방장만 처리할 수 있어요"),

    TAXI_POT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다");

    private final HttpStatus status;
    private final String message;
}
