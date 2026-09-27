package com.ktb.moyeota.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BankAccountRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("은행 코드와 계좌번호가 올바르면 통과한다")
    void acceptsValidRequest() {
        assertThat(violatedFields(new BankAccountRequest("shinhan", "110-123-45678"))).isEmpty();
    }

    @Test
    @DisplayName("은행명과 계좌번호는 둘 다 필수이고, 빠진 쪽을 가리킨다")
    void bothRequired() {
        assertThat(violatedFields(new BankAccountRequest(null, "11012345678"))).containsExactly("bankName");
        assertThat(violatedFields(new BankAccountRequest("shinhan", null))).containsExactly("accountNo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KB국민은행", "KB", "kbank"})
    @DisplayName("은행 코드가 아니면 INVALID_ENUM이다")
    void rejectsUnsupportedBank(String bankName) {
        assertThat(validator.validate(new BankAccountRequest(bankName, "11012345678")))
                .extracting(ConstraintViolation::getMessage).containsExactly("INVALID_ENUM");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "123456789012345", "110-123-4567a"})
    @DisplayName("계좌번호 형식이 틀리면 account_no를 가리킨다")
    void rejectsAccountNo(String accountNo) {
        assertThat(violatedFields(new BankAccountRequest("shinhan", accountNo))).containsExactly("accountNo");
    }

    @Test
    @DisplayName("계좌번호는 '-'를 제거한 값으로 명령에 담긴다")
    void normalizesAccountNo() {
        BankAccountCommand command = new BankAccountRequest("shinhan", "110-123-45678").toCommand();

        assertThat(command).isEqualTo(new BankAccountCommand("shinhan", "11012345678"));
    }

    private Set<String> violatedFields(BankAccountRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
