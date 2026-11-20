package com.tmw.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DecimalStringValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.RECORD_COMPONENT,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDecimalString {

    String message() default "Chuỗi số thập phân không đúng định dạng hoặc vượt quá giới hạn cho phép";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int maxIntegerDigits() default 15;

    int maxScale() default 4;

    boolean allowNegative() default false;
}