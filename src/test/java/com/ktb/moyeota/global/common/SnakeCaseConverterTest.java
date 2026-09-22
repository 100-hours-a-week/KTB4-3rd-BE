package com.ktb.moyeota.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class SnakeCaseConverterTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("자바 필드명을 API 명세의 snake_case로 바꾼다")
    @CsvSource({
            "nickname,        nickname",
            "termsAgreed,     terms_agreed",
            "profileImageUrl, profile_image_url",
            "bankName,        bank_name",
            "accountNo,       account_no",
            "clientMessageId, client_message_id",
            "departureAt,     departure_at",
            "address1,        address1"
    })
    void convert(String input, String expected) {
        assertThat(SnakeCaseConverter.convert(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("null과 빈 문자열은 그대로 둔다")
    @NullAndEmptySource
    void keepsNullAndEmpty(String input) {
        assertThat(SnakeCaseConverter.convert(input)).isEqualTo(input);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("연속된 대문자를 Jackson과 같은 방식으로 처리한다")
    @CsvSource({
            "profileURL,  profile_url",
            "userID,      user_id",
            "httpStatus,  http_status"
    })
    void handlesConsecutiveUppercaseLikeJackson(String input, String expected) {
        assertThat(SnakeCaseConverter.convert(input)).isEqualTo(expected);
    }
}
