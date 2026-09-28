package com.ktb.moyeota.domain.rating.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RatingSuccessCode implements SuccessCode {

    RATING_SUBMITTED("평가가 제출되었습니다");

    private final String message;
}
