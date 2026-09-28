package com.ktb.moyeota.domain.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BankAccountPairValidator implements ConstraintValidator<BankAccountPair, BankAccountFields> {

    private static final String REPORTED_FIELD = "bankName";

    @Override
    public boolean isValid(BankAccountFields value, ConstraintValidatorContext context) {
        if (value == null || (value.bankName() == null) == (value.accountNo() == null)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(REPORTED_FIELD)
                .addConstraintViolation();
        return false;
    }
}
