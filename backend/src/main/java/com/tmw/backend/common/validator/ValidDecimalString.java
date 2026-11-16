package com.tmw.backend.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DecimalStringValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDecimalString {
    
    // Thông báo lỗi mặc định khi validation thất bại
    String message() default "Invalid decimal format for money or quantity";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    // Cấu hình số chữ số phần thập phân tối đa (mặc định là 4 chữ số)
    int maxScale() default 4;
    
    // Cho phép số âm hay không (mặc định false)
    boolean allowNegative() default false;
}