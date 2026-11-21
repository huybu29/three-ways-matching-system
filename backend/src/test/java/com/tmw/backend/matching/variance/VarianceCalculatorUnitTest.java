package com.tmw.backend.matching.variance;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VarianceCalculatorUnitTest {

    private VarianceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new VarianceCalculator();
    }

    static Stream<Arguments> varianceScenarioTable() {
        // Ngưỡng chuẩn: lệch SL <= 2.0000%, lệch giá <= 1.0000%, lệch tiền tuyệt đối <= 50,000 VND
        ToleranceThreshold standardThreshold = new ToleranceThreshold(
                new BigDecimal("2.0000"),
                new BigDecimal("1.0000"),
                new BigDecimal("50000.0000"),
                true
        );

        return Stream.of(
                // 1. Khớp hoàn toàn
                Arguments.of(
                        "Khớp hoàn toàn SL và đơn giá",
                        "100", "100", "10000", "10000",
                        standardThreshold,
                        "0.0000", "0.0000", "0.0000", "0.0000", "0.0000",
                        true
                ),
                // 2a. Lệch dương trong ngưỡng (SL +1.5%, Giá +0.8%, Tiền +23,120)
                Arguments.of(
                        "Lệch dương nằm trong ngưỡng dung sai",
                        "101.5", "100", "10080", "10000",
                        standardThreshold,
                        "1.5000", "1.5000", "80.0000", "0.8000", "23120.0000",
                        true
                ),
                // 2b. Lệch âm chạm đúng biên ngưỡng (SL -2.0%, Giá -1.0%, Tiền -29,800)
                Arguments.of(
                        "Lệch âm chạm đúng biên ngưỡng (boundary)",
                        "98", "100", "9900", "10000",
                        standardThreshold,
                        "-2.0000", "2.0000", "-100.0000", "1.0000", "-29800.0000",
                        true
                ),
                // 3a. Lệch ngoài ngưỡng số lượng (SL +2.01% > 2.0%)
                Arguments.of(
                        "Lệch số lượng vượt ngoài ngưỡng",
                        "102.01", "100", "10000", "10000",
                        standardThreshold,
                        "2.0100", "2.0100", "0.0000", "0.0000", "20100.0000",
                        false
                ),
                // 3b. Lệch ngoài ngưỡng đơn giá (Giá +1.05% > 1.0%)
                Arguments.of(
                        "Lệch đơn giá vượt ngoài ngưỡng",
                        "100", "100", "10105", "10000",
                        standardThreshold,
                        "0.0000", "0.0000", "105.0000", "1.0500", "10500.0000",
                        false
                ),
                // 3c. % trong ngưỡng nhưng lệch thành tiền tuyệt đối vượt 50,000 VND
                Arguments.of(
                        "Tỷ lệ % trong ngưỡng nhưng tiền tuyệt đối vượt trần",
                        "101", "100", "100500", "100000",
                        standardThreshold,
                        "1.0000", "1.0000", "500.0000", "0.5000", "150500.0000",
                        false
                )
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("varianceScenarioTable")
    void shouldCalculateAndEvaluateVarianceAcrossAllScenarios(
            String scenarioName,
            String matchedQty,
            String referenceQty,
            String invoicePrice,
            String poPrice,
            ToleranceThreshold threshold,
            String expectedQtyVar,
            String expectedQtyVarPct,
            String expectedPriceVar,
            String expectedPriceVarPct,
            String expectedAmountVar,
            boolean expectedWithinTolerance
    ) {
        VarianceResult result = calculator.calculate(
                new BigDecimal(matchedQty),
                new BigDecimal(referenceQty),
                new BigDecimal(invoicePrice),
                new BigDecimal(poPrice),
                CurrencyCode.VND,
                CurrencyCode.VND
        );

        assertThat(result.qtyVariance()).isEqualByComparingTo(expectedQtyVar);
        assertThat(result.qtyVariancePct()).isEqualByComparingTo(expectedQtyVarPct);
        assertThat(result.priceVariance()).isEqualByComparingTo(expectedPriceVar);
        assertThat(result.priceVariancePct()).isEqualByComparingTo(expectedPriceVarPct);
        assertThat(result.amountVariance()).isEqualByComparingTo(expectedAmountVar);
        assertThat(calculator.isWithinTolerance(result, threshold)).isEqualTo(expectedWithinTolerance);
    }

    @Test
    void shouldApplyHalfUpRoundingTo4DecimalPlaces() {
        // SL: 101 / 300 = 33.666666...% -> làm tròn HALF_UP thành 33.6667%
        // Giá: 10003.33336 vs 10000 -> priceVariance = 3.3334, priceVariancePct = 0.0333%
        VarianceResult result = calculator.calculate(
                new BigDecimal("401"),
                new BigDecimal("300"),
                new BigDecimal("10003.33336"),
                new BigDecimal("10000"),
                CurrencyCode.VND,
                CurrencyCode.VND
        );

        assertThat(result.qtyVariancePct()).isEqualByComparingTo("33.6667");
        assertThat(result.priceVariance()).isEqualByComparingTo("3.3334");
        assertThat(result.priceVariancePct()).isEqualByComparingTo("0.0333");
    }

    @Test
    void shouldHandleZeroPoUnitPriceSafelyWithoutDivisionByZero() {
        VarianceResult bothZero = calculator.calculate(
                new BigDecimal("10"), new BigDecimal("10"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                CurrencyCode.VND, CurrencyCode.VND
        );
        assertThat(bothZero.priceVariancePct()).isEqualByComparingTo("0.0000");

        VarianceResult chargedAgainstFreePo = calculator.calculate(
                new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("5000"), BigDecimal.ZERO,
                CurrencyCode.VND, CurrencyCode.VND
        );
        assertThat(chargedAgainstFreePo.priceVariancePct()).isEqualByComparingTo("100.0000");
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("Giá hóa đơn âm", "10", "10", "-100", "1000", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Giá PO âm", "10", "10", "1000", "-0.01", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Số lượng hóa đơn bằng 0", "0", "10", "1000", "1000", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Số lượng hóa đơn âm", "-5", "10", "1000", "1000", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Số lượng tham chiếu bằng 0", "10", "0", "1000", "1000", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Thiếu giá hóa đơn (null)", "10", "10", null, "1000", CurrencyCode.VND, CurrencyCode.VND),
                Arguments.of("Lệch loại tiền tệ VND vs USD", "10", "10", "1000", "1000", CurrencyCode.VND, CurrencyCode.USD)
        );
    }

    @ParameterizedTest(name = "[{index}] Từ chối đầu vào lỗi: {0}")
    @MethodSource("invalidInputs")
    void shouldRejectNegativeOrInvalidInputsWithValidationError(
            String caseName,
            String matchedQty,
            String referenceQty,
            String invoicePrice,
            String poPrice,
            CurrencyCode invCurrency,
            CurrencyCode poCurrency
    ) {
        assertThatThrownBy(() -> calculator.calculate(
                matchedQty != null ? new BigDecimal(matchedQty) : null,
                referenceQty != null ? new BigDecimal(referenceQty) : null,
                invoicePrice != null ? new BigDecimal(invoicePrice) : null,
                poPrice != null ? new BigDecimal(poPrice) : null,
                invCurrency,
                poCurrency
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}