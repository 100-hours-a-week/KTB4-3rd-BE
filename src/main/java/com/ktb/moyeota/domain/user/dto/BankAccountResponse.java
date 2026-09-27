package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.MaskedBankAccount;

public record BankAccountResponse(String bankName, String accountNoMasked) {

    public static BankAccountResponse from(MaskedBankAccount account) {
        return new BankAccountResponse(account.bankName(), account.accountNoMasked());
    }
}
