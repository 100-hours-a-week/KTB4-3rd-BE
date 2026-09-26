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

    DEPARTURE_TIME_PASSED(HttpStatus.UNPROCESSABLE_CONTENT, "현재 시각 이후로 선택해주세요"),

    DEPARTURE_TIME_TOO_FAR(HttpStatus.UNPROCESSABLE_CONTENT, "3시간 이내로 선택해주세요"),

    SAME_ORIGIN_DEST(HttpStatus.UNPROCESSABLE_CONTENT, "출발지와 도착지는 다르게 설정해주세요"),

    BANK_ACCOUNT_REQUIRED(HttpStatus.CONFLICT, "정산 계좌를 먼저 등록해주세요"),

    MATCH_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 매칭이 있어요"),

    MATCH_BUSY(HttpStatus.CONFLICT, "요청이 몰려 매칭하지 못했어요. 잠시 후 다시 시도해주세요");

    private final HttpStatus status;
    private final String message;
}
