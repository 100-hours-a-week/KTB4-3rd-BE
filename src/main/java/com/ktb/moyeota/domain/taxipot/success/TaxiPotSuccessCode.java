package com.ktb.moyeota.domain.taxipot.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaxiPotSuccessCode implements SuccessCode {

    CURRENT_TAXI_POT_FOUND("조회에 성공했습니다"),

    NO_CURRENT_TAXI_POT("진행 중인 매칭이 없습니다");

    private final String message;
}
