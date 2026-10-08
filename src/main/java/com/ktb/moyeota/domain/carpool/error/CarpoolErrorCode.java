package com.ktb.moyeota.domain.carpool.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CarpoolErrorCode implements ErrorCode {

    HOST_ONLY(HttpStatus.FORBIDDEN, "카풀 등록자만 처리할 수 있습니다"),

    HOST_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "카풀 등록자는 운행 종료 이후에야 나갈 수 있습니다"),

    CARPOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카풀입니다"),

    DEPARTURE_TIME_PASSED(HttpStatus.UNPROCESSABLE_CONTENT, "현재 시각 이후로 선택해주세요"),

    DEPARTURE_TIME_TOO_FAR(HttpStatus.UNPROCESSABLE_CONTENT, "오늘 기준 한달 이내의 날짜만 선택 가능합니다"),

    SAME_ORIGIN_DEST(HttpStatus.UNPROCESSABLE_CONTENT, "출발지와 도착지가 같습니다"),

    CAR_REGISTRATION_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "차량 정보를 먼저 등록해주세요");

    private final HttpStatus status;
    private final String message;
}
