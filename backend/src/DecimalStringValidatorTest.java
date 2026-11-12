package com.tmw.backend.common.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecimalStringValidatorTest {

    private DecimalStringValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new DecimalStringValidator();
        context = mock(ConstraintValidatorContext.class);

        // Giả lập cấu hình của Annotation: maxScale = 4, allowNegative = false
        ValidDecimalString annotation = mock(ValidDecimalString.class);
        when(annotation.maxScale()).thenReturn(4);
        when(annotation.allowNegative()).thenReturn(false);

        validator.initialize(annotation);
    }

    @Test
    void shouldReturnTrue_WhenValueIsNull() {
        // Null sẽ do @NotNull lo, Validator này bỏ qua
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void shouldReturnTrue_WhenValueIsValid() {
        assertTrue(validator.isValid("1500.50", context));
        assertTrue(validator.isValid("0", context));
        assertTrue(validator.isValid("10.1234", context)); // Tối đa 4 số thập phân
    }

    @Test
    void shouldReturnFalse_WhenScaleIsExceeded() {
        // 5 số thập phân -> false (vì maxScale = 4)
        assertFalse(validator.isValid("10.12345", context)); 
    }

    @Test
    void shouldReturnFalse_WhenValueIsNegative() {
        // Số âm -> false (vì allowNegative = false)
        assertFalse(validator.isValid("-1500.50", context));
    }

    @Test
    void shouldReturnFalse_WhenValueIsInvalidString() {
        // Chữ cái hoặc ký tự lạ -> false
        assertFalse(validator.isValid("100abc", context));
        assertFalse(validator.isValid("100,50", context)); // Dấu phẩy không hợp lệ
    }
}