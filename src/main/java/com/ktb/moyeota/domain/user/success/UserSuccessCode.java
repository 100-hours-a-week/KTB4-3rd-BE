package com.ktb.moyeota.domain.user.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements SuccessCode {

    SIGNED_UP("가입이 완료되었어요"),

    NICKNAME_AVAILABLE("사용할 수 있는 닉네임이에요"),

    NICKNAME_TAKEN("이미 사용 중인 닉네임이에요");

    private final String message;
}
