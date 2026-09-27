package com.ktb.moyeota.domain.user.validation;

public final class AccountNoRules {

    public static final String PATTERN = "^\\d(?:-?\\d){9,13}$";

    private AccountNoRules() {
    }

    public static String normalize(String accountNo) {
        return accountNo.replace("-", "");
    }
}
