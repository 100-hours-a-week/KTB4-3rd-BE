package com.ktb.moyeota.domain.user.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankAccountPairValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("은행명과 계좌번호가 둘 다 있거나 둘 다 없으면 통과한다")
    void acceptsBothOrNeither() {
        assertThat(validator.validate(new Form("shinhan", "11012345678"))).isEmpty();
        assertThat(validator.validate(new Form(null, null))).isEmpty();
    }

    @Test
    @DisplayName("하나만 있으면 어느 쪽이 빠졌든 bankName을 가리켜 REQUIRED로 거부한다")
    void reportsOnBankName() {
        assertRejected(validator.validate(new Form("shinhan", null)));
        assertRejected(validator.validate(new Form(null, "11012345678")));
    }

    private void assertRejected(Set<ConstraintViolation<Form>> violations) {
        assertThat(violations).hasSize(1);
        ConstraintViolation<Form> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("bankName");
        assertThat(violation.getMessage()).isEqualTo("REQUIRED");
    }

    @BankAccountPair
    private record Form(String bankName, String accountNo) implements BankAccountFields {
    }
}
