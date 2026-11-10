package com.ktb.moyeota.domain.user.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    ACTIVE_HOST_EXISTS(HttpStatus.CONFLICT, "방장으로 진행 중인 동행이 있어 탈퇴할 수 없습니다"),

    ACTIVE_TAXI_POT_EXISTS(HttpStatus.CONFLICT, "참여 중인 택시팟이 있어 탈퇴할 수 없습니다"),

    CAR_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 차량입니다"),

    CAR_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 차량이 있습니다");

    private final HttpStatus status;
    private final String message;
}
