package com.ktb.moyeota.domain.user.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements SuccessCode {

    SIGNED_UP("가입이 완료되었어요"),

    NICKNAME_AVAILABLE("사용할 수 있는 닉네임이에요"),

    NICKNAME_TAKEN("이미 사용 중인 닉네임이에요"),

    MY_PROFILE_FOUND("내 정보 조회에 성공했습니다"),

    BANK_ACCOUNT_SAVED("정산 계좌가 저장되었습니다"),

    CAR_REGISTERED("차량이 등록되었습니다"),

    CAR_UPDATED("차량 정보가 수정되었습니다"),

    CARS_FOUND("조회에 성공했습니다"),

    NO_CAR("등록된 차량이 없습니다");

    private final String message;
}
