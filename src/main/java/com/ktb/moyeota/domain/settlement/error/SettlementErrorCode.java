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

    ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 정산 금액이 정해졌어요");

    private final HttpStatus status;
    private final String message;
}
