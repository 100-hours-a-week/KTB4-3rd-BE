package com.ktb.moyeota.global.exception;

import com.ktb.moyeota.global.common.SnakeCaseConverter;

public record ValidationDetail(String field, String reason) {

    public static ValidationDetail of(String field, ValidationReason reason) {
        return new ValidationDetail(SnakeCaseConverter.convert(field), reason.name());
    }
}
