package com.ktb.moyeota.domain.companionpost.exception;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CompanionPostErrorCode implements ErrorCode {

    COMPANION_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 동행모집 게시글입니다."),

    COMPANION_POST_CANCELED(HttpStatus.GONE, "취소된 동행모집 게시글입니다."),

    COMPANION_POST_DEPARTURE_AT_PAST(HttpStatus.UNPROCESSABLE_CONTENT, "출발 시간은 현재 이후여야 합니다"),

    COMPANION_POST_ORIGIN_DEST_SAME(HttpStatus.UNPROCESSABLE_CONTENT, "출발지와 도착지가 동일합니다"),

    COMPANION_POST_RECRUIT_COUNT_OUT_OF_RANGE(
            HttpStatus.UNPROCESSABLE_CONTENT, "모집 인원이 이동수단별 허용 범위를 벗어났습니다.");

    private final HttpStatus status;
    private final String message;
}
