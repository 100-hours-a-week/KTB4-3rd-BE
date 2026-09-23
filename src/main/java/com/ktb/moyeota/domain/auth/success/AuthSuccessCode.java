package com.ktb.moyeota.domain.auth.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements SuccessCode {

    ACCESS_TOKEN_REISSUED("액세스 토큰이 재발급되었습니다.");

    private final String message;
}
