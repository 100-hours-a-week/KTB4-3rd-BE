package com.ktb.moyeota.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgreementsRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("필수 약관 세 개는 하나라도 false면 그 항목을 가리켜 거부한다")
    void requiredAgreementsMustBeTrue() {
        assertThat(violated(new AgreementsRequest(false, true, true, false, false))).containsExactly("service");
        assertThat(violated(new AgreementsRequest(true, false, true, false, false))).containsExactly("location");
        assertThat(violated(new AgreementsRequest(true, true, false, false, false))).containsExactly("gender");
    }

    @Test
    @DisplayName("필수 약관 키를 빠뜨려도 거부한다")
    void omittedRequiredKeyIsRejected() {
        assertThat(violated(new AgreementsRequest(null, true, true, false, false))).containsExactly("service");
    }

    @Test
    @DisplayName("선택 약관은 false여도 되지만 키는 보내야 한다")
    void optionalAgreementsMayBeFalseButNotOmitted() {
        assertThat(violated(new AgreementsRequest(true, true, true, false, false))).isEmpty();
        assertThat(violated(new AgreementsRequest(true, true, true, null, false)))
                .containsExactly("accountThirdParty");
        assertThat(violated(new AgreementsRequest(true, true, true, false, null))).containsExactly("marketing");
    }

    @Test
    @DisplayName("명령에는 선택 약관 두 개만 담긴다. 필수 약관은 검증을 통과했으면 전부 true다")
    void commandCarriesOnlyOptionalAgreements() {
        assertThat(new AgreementsRequest(true, true, true, true, false).toCommand())
                .isEqualTo(new AgreementsCommand(true, false));
    }

    private Set<String> violated(AgreementsRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
