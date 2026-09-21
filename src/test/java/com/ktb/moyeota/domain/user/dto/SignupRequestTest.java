package com.ktb.moyeota.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignupRequestTest {

    private static final AgreementsRequest ALL_AGREED = new AgreementsRequest(true, true, true, true, false);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("모든 값이 올바르면 통과한다")
    void acceptsValidRequest() {
        assertThat(violatedFields(new SignupRequest("길동이", "MALE", null, null, null, ALL_AGREED))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"길", "열세글자가넘는닉네임입니다요", "길동 이", "길동이!", "길동이😀"})
    @DisplayName("닉네임은 2~12자의 한글·영문·숫자만 허용한다")
    void rejectsInvalidNickname(String nickname) {
        assertThat(violatedFields(new SignupRequest(nickname, "MALE", null, null, null, ALL_AGREED)))
                .containsExactly("nickname");
    }

    @Test
    @DisplayName("성별은 본문에서 필수이고 MALE·FEMALE만 허용한다")
    void genderIsRequiredEnum() {
        assertThat(violatedFields(new SignupRequest("길동이", null, null, null, null, ALL_AGREED))).containsExactly("gender");
        assertThat(violatedFields(new SignupRequest("길동이", "male", null, null, null, ALL_AGREED))).containsExactly("gender");
        assertThat(violatedFields(new SignupRequest("길동이", "OTHER", null, null, null, ALL_AGREED))).containsExactly("gender");
    }

    @Test
    @DisplayName("허용되지 않는 성별 값의 사유는 INVALID_ENUM이다")
    void invalidGenderReasonIsInvalidEnum() {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(new SignupRequest("길동이", "OTHER", null, null, null, ALL_AGREED));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("INVALID_ENUM");
    }

    @Test
    @DisplayName("프로필 이미지 키는 보내지 않아도 되고 500자를 넘을 수 없다")
    void profileImageKeyIsOptional() {
        assertThat(violatedFields(new SignupRequest("길동이", "MALE", "tmp/profile/s-1a2b/0b9c.jpg", null, null, ALL_AGREED)))
                .isEmpty();
        assertThat(violatedFields(new SignupRequest("길동이", "MALE", "k".repeat(501), null, null, ALL_AGREED)))
                .containsExactly("profileImageKey");
    }

    @Nested
    @DisplayName("정산 계좌")
    class BankAccount {

        @Test
        @DisplayName("계좌는 입력하지 않아도 된다")
        void bankAccountIsOptional() {
            assertThat(violatedWith(null, null)).isEmpty();
        }

        @Test
        @DisplayName("은행명과 계좌번호는 둘 다 보내거나 둘 다 생략한다. 어긋나면 bank_name을 가리킨다")
        void bankNameAndAccountNoComeTogether() {
            assertThat(violatedWith("shinhan", null)).containsExactly("bankName");
            assertThat(violatedWith(null, "11012345678")).containsExactly("bankName");
        }

        @ParameterizedTest
        @ValueSource(strings = {"kb", "shinhan", "woori", "hana", "nh", "ibk", "kakao", "toss"})
        @DisplayName("프론트의 BankCode 여덟 개를 받는다")
        void acceptsBankCodes(String bankCode) {
            assertThat(violatedWith(bankCode, "11012345678")).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"KB", "Shinhan", "신한은행", "KB국민은행", "citi", ""})
        @DisplayName("코드가 아닌 값은 INVALID_ENUM이다. 대문자·은행 이름·목록에 없는 코드 모두")
        void rejectsAnythingButBankCodes(String bankName) {
            assertThat(validator.validate(
                    new SignupRequest("길동이", "MALE", null, bankName, "11012345678", ALL_AGREED)))
                    .extracting(ConstraintViolation::getMessage).containsExactly("INVALID_ENUM");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1101234567", "12345678901234", "110-123-45678", "110-12345678"})
        @DisplayName("계좌번호는 '-'를 빼고 숫자 10~14자리면 된다")
        void acceptsAccountNo(String accountNo) {
            assertThat(violatedWith("shinhan", accountNo)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"123456789", "123456789012345", "110-123-4567a", "-11012345678", "110--12345678"})
        @DisplayName("자릿수가 안 맞거나 숫자·'-' 이외의 문자가 있으면 거부한다")
        void rejectsAccountNo(String accountNo) {
            assertThat(violatedWith("shinhan", accountNo)).containsExactly("accountNo");
        }

        @Test
        @DisplayName("계좌번호는 '-'를 제거한 값으로 명령에 담긴다")
        void normalizesAccountNo() {
            SignupCommand command =
                    new SignupRequest("길동이", "MALE", null, "shinhan", "110-123-45678", ALL_AGREED).toCommand();

            assertThat(command.bankAccount()).isEqualTo(new BankAccountCommand("shinhan", "11012345678"));
        }

        private Set<String> violatedWith(String bankName, String accountNo) {
            return violatedFields(new SignupRequest("길동이", "MALE", null, bankName, accountNo, ALL_AGREED));
        }
    }

    @Test
    @DisplayName("약관 객체를 아예 보내지 않으면 거부한다")
    void omittedAgreements() {
        assertThat(violatedFields(new SignupRequest("길동이", "MALE", null, null, null, null)))
                .containsExactly("agreements");
    }

    @Test
    @DisplayName("약관 객체 안의 위반은 agreements.항목 경로로 드러난다")
    void nestedViolationPath() {
        AgreementsRequest declined = new AgreementsRequest(true, false, true, false, false);

        assertThat(violatedFields(new SignupRequest("길동이", "MALE", null, null, null, declined)))
                .containsExactly("agreements.location");
    }

    @Test
    @DisplayName("검증을 통과한 요청은 성별을 enum으로, 선택 약관만 담은 명령이 된다. 계좌가 없으면 null이다")
    void convertsToCommand() {
        assertThat(new SignupRequest("길동이", "FEMALE", null, null, null, ALL_AGREED).toCommand())
                .isEqualTo(new SignupCommand("길동이", Gender.FEMALE, null, new AgreementsCommand(true, false)));
    }

    private Set<String> violatedFields(SignupRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
