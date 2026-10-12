package com.ktb.moyeota.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PushTokenRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("토큰이 있으면 통과한다")
    void acceptsToken() {
        assertThat(validator.validate(new PushTokenRequest("fcm-token"))).isEmpty();
    }

    @Test
    @DisplayName("255자까지는 통과한다")
    void acceptsMaxLength() {
        assertThat(validator.validate(new PushTokenRequest("a".repeat(PushTokenRequest.MAX_LENGTH)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("토큰이 비어 있으면 NotBlank 위반이다")
    void rejectsBlank(String token) {
        assertThat(violatedConstraints(new PushTokenRequest(token))).containsExactly("NotBlank");
    }

    @Test
    @DisplayName("255자를 넘으면 Size 위반이다")
    void rejectsTooLong() {
        assertThat(violatedConstraints(new PushTokenRequest("a".repeat(PushTokenRequest.MAX_LENGTH + 1))))
                .containsExactly("Size");
    }

    private Set<String> violatedConstraints(PushTokenRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getConstraintDescriptor)
                .map(descriptor -> descriptor.getAnnotation().annotationType().getSimpleName())
                .collect(Collectors.toSet());
    }
}
