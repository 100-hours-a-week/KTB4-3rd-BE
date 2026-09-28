package com.ktb.moyeota.domain.user.validation;

public final class NicknameRules {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 12;
    public static final String PATTERN = "^[가-힣A-Za-z0-9]+$";

    private NicknameRules() {
    }
}
