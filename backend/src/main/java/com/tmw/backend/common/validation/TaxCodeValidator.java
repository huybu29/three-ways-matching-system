package com.tmw.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import com.tmw.backend.common.validation.ValidTaxCode;
import java.util.regex.Pattern;

public class TaxCodeValidator implements ConstraintValidator<ValidTaxCode, String> {

    public static final String TAX_CODE_REGEX = "^[0-9]{10}(-[0-9]{3})?$";
    private static final Pattern TAX_CODE_PATTERN = Pattern.compile(TAX_CODE_REGEX);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return TAX_CODE_PATTERN.matcher(value).matches();
    }
}