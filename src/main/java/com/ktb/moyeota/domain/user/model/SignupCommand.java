package com.ktb.moyeota.domain.user.model;

import com.ktb.moyeota.domain.user.entity.Gender;

public record SignupCommand(
        String nickname,
        Gender gender,
        String profileImageKey,
        BankAccountCommand bankAccount,
        AgreementsCommand agreements) {
}
