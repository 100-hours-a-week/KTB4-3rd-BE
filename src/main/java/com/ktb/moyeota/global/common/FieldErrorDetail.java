package com.ktb.moyeota.global.common;

import lombok.Getter;

@Getter
public class FieldErrorDetail {

    private final String field;
    private final String reason;

    private FieldErrorDetail(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }

    public static FieldErrorDetail of(String field, String reason) {
        return new FieldErrorDetail(field, reason);
    }
}
