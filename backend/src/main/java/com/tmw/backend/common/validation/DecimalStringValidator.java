package com.tmw.backend.common.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import com.tmw.backend.common.validation.ValidDecimalString;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DecimalStringValidator implements ConstraintValidator<ValidDecimalString, String> {

    private int maxIntegerDigits;
    private int maxScale;
    private boolean allowNegative;
    private Pattern pattern;

    @Override
    public void initialize(ValidDecimalString constraintAnnotation) {
        this.maxIntegerDigits = constraintAnnotation.maxIntegerDigits();
        this.maxScale = constraintAnnotation.maxScale();
        this.allowNegative = constraintAnnotation.allowNegative();

        String signPrefix = allowNegative ? "-?" : "";
        String scalePart = maxScale > 0 ? "(\\.\\d{1," + maxScale + "})?" : "";
        this.pattern = Pattern.compile("^" + signPrefix + "\\d{1," + maxIntegerDigits + "}" + scalePart + "$");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
       
        if (value == null) {
            return true;
        }

        if (!pattern.matcher(value).matches()) {
            return false;
        }

       
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (!allowNegative && decimal.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            int integerDigits = decimal.precision() - decimal.scale();
            return integerDigits <= maxIntegerDigits && decimal.scale() <= maxScale;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}