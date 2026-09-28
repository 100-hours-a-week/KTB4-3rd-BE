package com.ktb.moyeota.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskedBankAccountTest {

    @ParameterizedTest(name = "{0} -> {1}", quoteTextArguments = false)
    @CsvSource({
            "1101234567, ******4567",
            "11012345678, *******5678",
            "12345678901234, **********1234"
    })
    @DisplayName("뒤 4자리를 제외한 모든 자리를 '*'로 바꾸고 자릿수는 유지한다")
    void masksAllButLastFourDigits(String accountNo, String masked) {
        assertThat(MaskedBankAccount.of("shinhan", accountNo))
                .isEqualTo(new MaskedBankAccount("shinhan", masked));
    }
}
