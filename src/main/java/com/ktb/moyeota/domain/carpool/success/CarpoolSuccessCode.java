package com.ktb.moyeota.domain.carpool.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarpoolSuccessCode implements SuccessCode {

    PINS_FOUND("조회에 성공했습니다"),

    TOO_MANY_PINS("표시할 핀이 많습니다. 지도를 확대해주세요");

    private final String message;
}
