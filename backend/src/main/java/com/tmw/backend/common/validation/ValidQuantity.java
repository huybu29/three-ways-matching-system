package com.tmw.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@ValidDecimalString(maxIntegerDigits = 15, maxScale = 4, allowNegative = false)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuantity {

    String message() default "Số lượng phải là chuỗi số thập phân không âm hợp lệ (tối đa 15 chữ số nguyên và 4 chữ số thập phân)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}