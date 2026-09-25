package com.ktb.moyeota.domain.taxipot.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaxiPotErrorCode implements ErrorCode {

    HOST_ONLY(HttpStatus.FORBIDDEN, "방장만 처리할 수 있어요"),

    TAXI_POT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다"),

    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "유효하지 않은 상태 변경입니다"),

    NOT_ENOUGH_PARTICIPANTS(HttpStatus.CONFLICT, "2명 이상 모여야 출발할 수 있어요"),

    DEPARTURE_NOT_REACHED(HttpStatus.CONFLICT, "출발 시각 이후에 시작할 수 있어요");

    private final HttpStatus status;
    private final String message;
}
