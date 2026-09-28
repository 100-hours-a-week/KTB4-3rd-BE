package com.ktb.moyeota.domain.image.dto;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.domain.image.model.PresignedUrlCommand;
import com.ktb.moyeota.domain.image.validation.ImagePolicyFields;
import com.ktb.moyeota.domain.image.validation.WithinImagePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@WithinImagePolicy
public record PresignedUrlRequest(
        @NotBlank
        @Pattern(regexp = "^(PROFILE)$", message = "INVALID_ENUM")
        String purpose,

        @NotBlank
        @Pattern(regexp = "^(image/jpeg|image/png|image/webp)$", message = "INVALID_ENUM")
        String contentType,

        @NotNull
        @Positive
        Long contentLength) implements ImagePolicyFields {

    public PresignedUrlCommand toCommand() {
        return new PresignedUrlCommand(
                ImagePurpose.valueOf(purpose),
                ImageType.fromContentType(contentType).orElseThrow(),
                contentLength);
    }
}
