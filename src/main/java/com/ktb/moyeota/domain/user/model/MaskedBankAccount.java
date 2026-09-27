package com.ktb.moyeota.domain.user.model;

public record MaskedBankAccount(String bankName, String accountNoMasked) {

    private static final int VISIBLE_DIGITS = 4;

    public static MaskedBankAccount of(String bankName, String accountNo) {
        int hidden = accountNo.length() - VISIBLE_DIGITS;
        return new MaskedBankAccount(bankName, "*".repeat(hidden) + accountNo.substring(hidden));
    }
}
