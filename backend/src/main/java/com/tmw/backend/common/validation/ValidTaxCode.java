package com.tmw.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TaxCodeValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTaxCode {

    String message() default "Mã số thuế không hợp lệ (phải gồm 10 chữ số hoặc định dạng 10 chữ số kèm mã chi nhánh 3 chữ số, ví dụ: 0312345678 hoặc 0312345678-001)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}