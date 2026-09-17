package com.ktb.moyeota.global.exception;

public enum ValidationReason {

    REQUIRED,

    LENGTH_OUT_OF_RANGE,

    INVALID_FORMAT,

    INVALID_ENUM,

    OUT_OF_RANGE;

    public static ValidationReason from(String annotationName, String message) {
        ValidationReason declared = parse(message);
        return declared != null ? declared : fromAnnotation(annotationName);
    }

    private static ValidationReason parse(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        for (ValidationReason reason : values()) {
            if (reason.name().equals(message)) {
                return reason;
            }
        }
        return null;
    }

    private static ValidationReason fromAnnotation(String annotationName) {
        if (annotationName == null) {
            return INVALID_FORMAT;
        }
        return switch (annotationName) {
            case "NotNull", "NotBlank", "NotEmpty", "AssertTrue", "AssertFalse" -> REQUIRED;
            case "Size", "Length" -> LENGTH_OUT_OF_RANGE;
            case "Min", "Max", "DecimalMin", "DecimalMax", "Digits", "Range",
                 "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
                 "Past", "PastOrPresent", "Future", "FutureOrPresent" -> OUT_OF_RANGE;
            case "Pattern", "Email", "URL" -> INVALID_FORMAT;
            default -> INVALID_FORMAT;
        };
    }
}
