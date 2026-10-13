package com.ktb.moyeota.domain.settlement.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementSuccessCode implements SuccessCode {

    AMOUNT_ENTERED("수동으로 입력받은 값으로 정산을 진행합니다."),

    METER_ANALYSIS_FOUND("미터기 금액 인식 상태를 조회했습니다"),

    MY_TRANSFER_FOUND("내 송금 상태를 조회했습니다"),

    TRANSFER_PROOF_FOUND("송금 인증 사진을 조회했습니다"),

    SETTLEMENT_FOUND("정산 상태를 조회했습니다");

    private final String message;
}
