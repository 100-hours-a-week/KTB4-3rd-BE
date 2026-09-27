package com.ktb.moyeota.domain.rating.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RatingErrorCode implements ErrorCode {

    RATABLE_COMPANION_NOT_FOUND(HttpStatus.NOT_FOUND, "평가할 수 있는 동행이 아닙니다"),

    INVALID_RATING_TARGET(HttpStatus.UNPROCESSABLE_CONTENT, "평가할 수 없는 사용자입니다"),

    RATING_TARGET_MISSING(HttpStatus.UNPROCESSABLE_CONTENT, "함께 탄 사람을 모두 평가해주세요"),

    ALREADY_RATED(HttpStatus.CONFLICT, "이미 평가를 제출했습니다");

    private final HttpStatus status;
    private final String message;
}
