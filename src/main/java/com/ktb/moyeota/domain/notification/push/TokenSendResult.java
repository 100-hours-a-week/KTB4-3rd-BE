package com.ktb.moyeota.domain.notification.push;


public record TokenSendResult(
        String token,
        TokenSendOutcome outcome,
        String errorCode
) {

    public static TokenSendResult success(String token) {
        return new TokenSendResult(token, TokenSendOutcome.SUCCESS, null);
    }

    public static TokenSendResult failure(String token, TokenSendOutcome outcome, String errorCode) {
        return new TokenSendResult(token, outcome, errorCode);
    }
}
