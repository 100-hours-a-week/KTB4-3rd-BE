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

    CAR_REGISTRATION_REQUIRED(HttpStatus.CONFLICT, "차량 정보를 먼저 등록해주세요"),

    OWN_CARPOOL(HttpStatus.UNPROCESSABLE_CONTENT, "본인이 등록한 카풀에는 동승 요청을 보낼 수 없습니다"),

    CARPOOL_CLOSED(HttpStatus.CONFLICT, "마감된 카풀입니다"),

    CAPACITY_FULL(HttpStatus.CONFLICT, "카풀 정원이 가득 찼습니다"),

    ALREADY_PARTICIPATING(HttpStatus.CONFLICT, "이미 참여 중인 카풀입니다"),

    REQUEST_ALREADY_PENDING(HttpStatus.CONFLICT, "이미 요청을 보낸 상태입니다"),

    REQUEST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "제한된 요청 수를 초과했습니다"),

    CARPOOL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카풀 요청입니다"),

    REQUEST_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 요청입니다");

    private final HttpStatus status;
    private final String message;
}
