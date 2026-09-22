package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import com.ktb.moyeota.domain.user.validation.BankAccountFields;
import com.ktb.moyeota.domain.user.validation.BankAccountPair;
import com.ktb.moyeota.domain.user.validation.NicknameRules;
import com.ktb.moyeota.domain.user.validation.SupportedBanks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@BankAccountPair
public record SignupRequest(
        @NotBlank
        @Size(min = NicknameRules.MIN_LENGTH, max = NicknameRules.MAX_LENGTH)
        @Pattern(regexp = NicknameRules.PATTERN)
        String nickname,

        @NotBlank
        @Pattern(regexp = "^(MALE|FEMALE)$", message = "INVALID_ENUM")
        String gender,

        @Size(max = 500)
        String profileImageKey,

        @Pattern(regexp = SupportedBanks.PATTERN, message = "INVALID_ENUM")
        String bankName,

        @Pattern(regexp = "^\\d(?:-?\\d){9,13}$")
        String accountNo,

        @NotNull
        @Valid
        AgreementsRequest agreements) implements BankAccountFields {

    public SignupCommand toCommand() {
        return new SignupCommand(nickname, Gender.valueOf(gender), bankAccount(), agreements.toCommand());
    }

    private BankAccountCommand bankAccount() {
        if (bankName == null) {
            return null;
        }
        return new BankAccountCommand(bankName, accountNo.replace("-", ""));
    }
}
