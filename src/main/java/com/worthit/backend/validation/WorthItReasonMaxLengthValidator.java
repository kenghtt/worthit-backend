package com.worthit.backend.validation;

import com.worthit.backend.config.SubmitProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorthItReasonMaxLengthValidator implements ConstraintValidator<WorthItReasonMaxLength, String> {

    private final SubmitProperties submitProperties;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        int maxLength = submitProperties.getWorthItReasonMaxLength();
        if (value.length() <= maxLength) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be at most " + maxLength + " characters")
                .addConstraintViolation();
        return false;
    }
}