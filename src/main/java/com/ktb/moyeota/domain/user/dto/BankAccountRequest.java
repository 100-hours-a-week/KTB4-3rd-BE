package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.validation.AccountNoRules;
import com.ktb.moyeota.domain.user.validation.SupportedBanks;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankAccountRequest(
        @NotBlank
        @Pattern(regexp = SupportedBanks.PATTERN, message = "INVALID_ENUM")
        String bankName,

        @NotBlank
        @Pattern(regexp = AccountNoRules.PATTERN)
        String accountNo) {

    public BankAccountCommand toCommand() {
        return new BankAccountCommand(bankName, AccountNoRules.normalize(accountNo));
    }
}
