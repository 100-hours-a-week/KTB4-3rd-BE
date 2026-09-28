package com.ktb.moyeota.domain.image.validation;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.global.exception.ValidationReason;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

public class WithinImagePolicyValidator implements ConstraintValidator<WithinImagePolicy, ImagePolicyFields> {

    @Override
    public boolean isValid(ImagePolicyFields value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Optional<ImagePurpose> purpose = ImagePurpose.fromName(value.purpose());
        Optional<ImageType> type = ImageType.fromContentType(value.contentType());
        if (purpose.isEmpty() || type.isEmpty() || value.contentLength() == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (!purpose.get().allows(type.get())) {
            reject(context, "contentType", ValidationReason.INVALID_ENUM);
            valid = false;
        }
        if (!purpose.get().fits(value.contentLength())) {
            reject(context, "contentLength", ValidationReason.OUT_OF_RANGE);
            valid = false;
        }
        return valid;
    }

    private static void reject(ConstraintValidatorContext context, String field, ValidationReason reason) {
        context.buildConstraintViolationWithTemplate(reason.name())
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
