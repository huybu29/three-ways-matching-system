package com.tmw.backend.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest {

    private static Validator validator;

    record MoneySample(@ValidMoney String amount) {}
    record QuantitySample(@ValidQuantity String qty) {}
    record TaxCodeSample(@ValidTaxCode String taxCode) {}

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("@ValidMoney tests")
    class ValidMoneyTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "0",
                "1500000",
                "-250000",
                "10.5",
                "10.1234",
                "-0.0001",
                "999999999999999.9999"
        })
        void shouldAcceptValidMoneyStrings(String validValue) {
            Set<ConstraintViolation<MoneySample>> violations = validator.validate(new MoneySample(validValue));
            assertThat(violations).isEmpty();
        }

        @Test
        void shouldAllowNullValue() {
            assertThat(validator.validate(new MoneySample(null))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                " 100 ",
                "+100",
                "100.",
                ".5",
                "10.12345",
                "1000000000000000",
                "1e5",
                "1,500,000",
                "abc"
        })
        void shouldRejectInvalidMoneyStrings(String invalidValue) {
            Set<ConstraintViolation<MoneySample>> violations = validator.validate(new MoneySample(invalidValue));
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .contains("Giá trị tiền tệ phải là chuỗi số thập phân hợp lệ");
        }
    }

    @Nested
    @DisplayName("@ValidQuantity tests")
    class ValidQuantityTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "0",
                "1",
                "10.5",
                "125.1234",
                "999999999999999.9999"
        })
        void shouldAcceptValidQuantityStrings(String validValue) {
            assertThat(validator.validate(new QuantitySample(validValue))).isEmpty();
        }

        @Test
        void shouldAllowNullValue() {
            assertThat(validator.validate(new QuantitySample(null))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "-1",
                "-0.0001",
                "",
                " 5 ",
                "+5",
                "5.",
                ".25",
                "5.12345",
                "1000000000000000",
                "NaN"
        })
        void shouldRejectInvalidQuantityStrings(String invalidValue) {
            Set<ConstraintViolation<QuantitySample>> violations = validator.validate(new QuantitySample(invalidValue));
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .contains("Số lượng phải là chuỗi số thập phân không âm hợp lệ");
        }
    }

    @Nested
    @DisplayName("@ValidTaxCode tests")
    class ValidTaxCodeTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "0312345678",
                "0100109106-001",
                "0000000000-999"
        })
        void shouldAcceptValidTaxCodes(String validTaxCode) {
            assertThat(validator.validate(new TaxCodeSample(validTaxCode))).isEmpty();
        }

        @Test
        void shouldAllowNullValue() {
            assertThat(validator.validate(new TaxCodeSample(null))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                " 0312345678 ",
                "031234567",
                "03123456789",
                "0312345678-",
                "0312345678-01",
                "0312345678-0001",
                "031234567A",
                "0312345678_001"
        })
        void shouldRejectInvalidTaxCodes(String invalidTaxCode) {
            Set<ConstraintViolation<TaxCodeSample>> violations = validator.validate(new TaxCodeSample(invalidTaxCode));
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .contains("Mã số thuế không hợp lệ");
        }
    }
}