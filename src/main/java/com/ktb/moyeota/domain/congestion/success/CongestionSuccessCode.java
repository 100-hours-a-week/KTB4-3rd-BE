package com.ktb.moyeota.domain.congestion.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CongestionSuccessCode implements SuccessCode {

    SPOT_FOUND("조회에 성공했습니다"),

    SPOT_REASON_MISSING("혼잡 원인 정보 없음"),

    SPOT_ANALYZING("혼잡 원인 분석 중");

    private final String message;
}
