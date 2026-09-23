package com.ktb.moyeota.domain.image.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.domain.image.model.PresignedUrlCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PresignedUrlRequestTest {

    private static final long FIVE_MB = 5L * 1024 * 1024;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("모든 값이 올바르면 통과한다")
    void acceptsValidRequest() {
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/jpeg", 482_113L))).isEmpty();
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/webp", FIVE_MB))).isEmpty();
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/png", 1L))).isEmpty();
    }

    @Test
    @DisplayName("세 필드 모두 필수다")
    void allFieldsRequired() {
        assertThat(violations(new PresignedUrlRequest(null, null, null)))
                .containsOnly(
                        Map.entry("purpose", "REQUIRED"),
                        Map.entry("contentType", "REQUIRED"),
                        Map.entry("contentLength", "REQUIRED"));
    }

    @Test
    @DisplayName("지원하지 않는 용도는 INVALID_ENUM이다")
    void unsupportedPurpose() {
        assertThat(violations(new PresignedUrlRequest("POST", "image/jpeg", 100L)))
                .containsExactly(Map.entry("purpose", "INVALID_ENUM"));
    }

    @Test
    @DisplayName("이미지가 아닌 타입은 INVALID_ENUM이다")
    void unsupportedContentType() {
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/gif", 100L)))
                .containsExactly(Map.entry("contentType", "INVALID_ENUM"));
        assertThat(violations(new PresignedUrlRequest("PROFILE", "application/pdf", 100L)))
                .containsExactly(Map.entry("contentType", "INVALID_ENUM"));
    }

    @Test
    @DisplayName("크기는 1바이트 이상 용도별 상한(프로필 5MB) 이하다")
    void contentLengthWithinPolicy() {
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/jpeg", 0L)))
                .containsExactly(Map.entry("contentLength", "OUT_OF_RANGE"));
        assertThat(violations(new PresignedUrlRequest("PROFILE", "image/jpeg", FIVE_MB + 1)))
                .containsExactly(Map.entry("contentLength", "OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("커맨드로 바꾸면 enum과 타입이 해석된다")
    void toCommand() {
        assertThat(new PresignedUrlRequest("PROFILE", "image/webp", 100L).toCommand())
                .isEqualTo(new PresignedUrlCommand(ImagePurpose.PROFILE, ImageType.WEBP, 100L));
    }

    private Map<String, String> violations(PresignedUrlRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        PresignedUrlRequestTest::reasonOf,
                        (a, b) -> a + "," + b));
    }

    private static String reasonOf(ConstraintViolation<?> violation) {
        String message = violation.getMessage();
        if (message.equals("INVALID_ENUM") || message.equals("OUT_OF_RANGE")) {
            return message;
        }
        String annotation = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return switch (annotation) {
            case "NotBlank", "NotNull" -> "REQUIRED";
            case "Positive" -> "OUT_OF_RANGE";
            default -> annotation;
        };
    }
}
