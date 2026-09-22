package com.ktb.moyeota.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidationReasonTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("애노테이션 이름을 기본 사유로 매핑한다")
    @CsvSource({
            "NotBlank, REQUIRED",
            "AssertTrue, REQUIRED",
            "Size, LENGTH_OUT_OF_RANGE",
            "Min, OUT_OF_RANGE",
            "Past, OUT_OF_RANGE",
            "Pattern, INVALID_FORMAT"
    })
    void mapsAnnotationName(String annotation, ValidationReason expected) {
        assertThat(ValidationReason.from(annotation, null)).isEqualTo(expected);
    }

    @Test
    @DisplayName("제약에 선언한 message가 애노테이션 매핑을 덮어쓴다")
    void declaredMessageWins() {
        assertThat(ValidationReason.from("Pattern", "INVALID_ENUM"))
                .isEqualTo(ValidationReason.INVALID_ENUM);
        assertThat(ValidationReason.from("Size", "REQUIRED"))
                .isEqualTo(ValidationReason.REQUIRED);
    }

    @Test
    @DisplayName("Bean Validation 기본 문구는 사유로 해석하지 않는다")
    void defaultMessageIsIgnored() {
        assertThat(ValidationReason.from("Size", "size must be between 2 and 12"))
                .isEqualTo(ValidationReason.LENGTH_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("매핑에 없는 커스텀 제약은 INVALID_FORMAT으로 떨어진다")
    void unmappedAnnotationFallsBack() {
        assertThat(ValidationReason.from("BankAccountPair", null))
                .isEqualTo(ValidationReason.INVALID_FORMAT);
    }
}
