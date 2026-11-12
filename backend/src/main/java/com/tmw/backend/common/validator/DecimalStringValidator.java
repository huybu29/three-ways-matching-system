package com.tmw.backend.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class DecimalStringValidator implements ConstraintValidator<ValidDecimalString, String> {

    private int maxScale;
    private boolean allowNegative;

    @Override
    public void initialize(ValidDecimalString constraintAnnotation) {
        this.maxScale = constraintAnnotation.maxScale();
        this.allowNegative = constraintAnnotation.allowNegative();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Nếu giá trị truyền vào là null thì bỏ qua (dùng kết hợp @NotNull nếu bắt buộc có)
        if (value == null) {
            return true;
        }

        try {
            // Chuyển chuỗi thành BigDecimal để kiểm tra chính xác số tiền/số lượng
            BigDecimal decimal = new BigDecimal(value);
            
            // Nếu không cho phép số âm mà giá trị nhỏ hơn 0 thì báo lỗi
            if (!allowNegative && decimal.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            
            // Kiểm tra số chữ số phần thập phân có vượt quá giới hạn maxScale không
            if (decimal.scale() > maxScale) {
                return false;
            }

            return true;
        } catch (NumberFormatException e) {
            // Nếu chuỗi nhập vào không phải định dạng số hợp lệ -> báo lỗi
            return false;
        }
    }
}