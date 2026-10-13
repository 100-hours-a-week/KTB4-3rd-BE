package com.ktb.moyeota.domain.settlement.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {

    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산입니다"),

    PAYEE_HAS_NO_TRANSFER(HttpStatus.FORBIDDEN, "방장은 송금할 금액이 없어요"),

    TRANSFER_PROOF_FORBIDDEN(HttpStatus.FORBIDDEN, "방장 또는 본인의 송금 내역만 조회할 수 있습니다"),

    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "송금 내역이 없습니다"),

    ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 정산 금액이 정해졌어요"),

    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "정산을 완료할 수 있는 상태가 아니에요");

    private final HttpStatus status;
    private final String message;
}
