package com.ktb.moyeota.domain.carpool.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarpoolSuccessCode implements SuccessCode {

    PINS_FOUND("조회에 성공했습니다"),

    TOO_MANY_PINS("표시할 핀이 많습니다. 지도를 확대해주세요"),

    NEARBY_CARPOOLS_FOUND("조회에 성공했습니다"),

    STATUS_CHANGED("운행 상태가 변경됐어요"),

    CARPOOL_CREATED("카풀 등록을 성공했습니다"),

    DETAIL_FOUND("조회에 성공했습니다"),

    JOIN_REQUEST_SENT("카풀 요청이 등록되었습니다"),

    JOIN_REQUEST_ACCEPTED("요청을 수락했습니다"),

    JOIN_REQUEST_REJECTED("요청을 거절했습니다");

    private final String message;
}
