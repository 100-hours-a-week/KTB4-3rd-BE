package com.ktb.moyeota.global.external.ai;

import java.util.Arrays;
import java.util.Optional;

enum AiErrorCode {

    REQUEST_INVALID(Category.SYSTEM),
    IMAGE_FETCH_FAILED(Category.SYSTEM),
    IMAGE_SIZE_INVALID(Category.UNREADABLE),
    IMAGE_TYPE_INVALID(Category.UNREADABLE),
    JUDGE_PARSE_FAILED(Category.UNREADABLE),
    UPSTREAM_FAILED(Category.SYSTEM),
    MODEL_NOT_READY(Category.SYSTEM),
    INTERNAL(Category.SYSTEM);

    enum Category { UNREADABLE, SYSTEM }

    private final Category category;

    AiErrorCode(Category category) {
        this.category = category;
    }

    static Optional<AiErrorCode> fromCode(String code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.name().equals(code))
                .findFirst();
    }

    boolean isUnreadable() {
        return category == Category.UNREADABLE;
    }
}
