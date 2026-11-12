package com.ktb.moyeota.domain.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemMessageText {

    METER_REQUESTED("택시 미터기 사진을 보내주세요"),

    TRANSFER_REQUESTED("방장이 정산을 요청했어요"),

    SETTLEMENT_COMPLETED("정산이 완료됐어요"),

    TRANSFER_UNVERIFIED("송금 확인이 어려워요. 대면으로 송금 내역을 확인한 뒤 방장이 정산을 완료해주세요"),

    SETTLEMENT_CONFIRM_REQUESTED("모두의 송금이 정상적으로 도착했나요?");

    private final String text;
}
