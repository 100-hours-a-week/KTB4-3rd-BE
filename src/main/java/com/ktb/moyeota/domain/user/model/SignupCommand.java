package com.ktb.moyeota.domain.user.model;

import com.ktb.moyeota.domain.user.entity.Gender;

public record SignupCommand(
        String nickname, Gender gender, BankAccountCommand bankAccount, AgreementsCommand agreements) {
}
