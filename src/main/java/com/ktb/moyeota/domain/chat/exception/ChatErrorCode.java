package com.ktb.moyeota.domain.chat.exception;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),

    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다."),

    COMPANION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 동행입니다."),

    COMPANION_NOT_JOINABLE(HttpStatus.CONFLICT, "참여할 수 없는 동행입니다."),

    ALREADY_PARTICIPATING(HttpStatus.CONFLICT, "이미 참여 중인 동행입니다."),

    NOT_PARTICIPATING(HttpStatus.NOT_FOUND, "참여 중인 동행이 아닙니다."),

    COMPANION_CHATROOM_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "동행에 대한 채팅방을 찾을 수 없습니다."),

    COMPANION_NEXT_HOST_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "다음 방장을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
