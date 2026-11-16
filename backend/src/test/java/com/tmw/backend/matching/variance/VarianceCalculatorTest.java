package com.tmw.backend.matching.variance;

import com.tmw.backend.matching.entity.MatchType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VarianceCalculatorTest {

    private final VarianceCalculator calc = new VarianceCalculator();

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Nested
    class ThreeWay {

        @Test
        void exactMatch_noVariance() {
            var r = calc.calculate(VarianceInput.threeWay(d("100"), d("50000"), d("50000"), d("100")));

            assertThat(r.isExactMatch()).isTrue();
            assertThat(r.qtyVariance()).isEqualByComparingTo("0");
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("0");
            assertThat(r.priceVariancePct()).isEqualByComparingTo("0");
            assertThat(r.invoiceAmount()).isEqualByComparingTo("5000000");
            assertThat(r.expectedAmount()).isEqualByComparingTo("5000000");
        }

        @Test
        void invoicedMoreThanReceived_positiveQtyVariance() {
            // nhận kho 95, hóa đơn ghi 100
            var r = calc.calculate(VarianceInput.threeWay(d("100"), d("50000"), d("50000"), d("95")));

            assertThat(r.referenceQty()).isEqualByComparingTo("95");
            assertThat(r.qtyVariance()).isEqualByComparingTo("5");
            // 5 / 95 * 100 = 5.263157... → 5.2632
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("5.2632");
            assertThat(r.amountVariance()).isEqualByComparingTo("250000"); // 5 * 50,000
            assertThat(r.hasPriceVariance()).isFalse();
        }

        @Test
        void invoicedLessThanReceived_negativeQtyVariance() {
            var r = calc.calculate(VarianceInput.threeWay(d("80"), d("10"), d("10"), d("100")));

            assertThat(r.qtyVariance()).isEqualByComparingTo("-20");
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("-20");
            assertThat(r.amountVariance()).isEqualByComparingTo("-200");
        }

        @Test
        void priceHigherThanPo_positivePriceVariance() {
            // PO 200,000; hóa đơn 204,000 → +2%
            var r = calc.calculate(VarianceInput.threeWay(d("10"), d("204000"), d("200000"), d("10")));

            assertThat(r.priceVariance()).isEqualByComparingTo("4000");
            assertThat(r.priceVariancePct()).isEqualByComparingTo("2");
            assertThat(r.hasQtyVariance()).isFalse();
            assertThat(r.amountVariance()).isEqualByComparingTo("40000");
        }

        @Test
        void bothQtyAndPriceVariance_amountVarianceCombines() {
            // hóa đơn 12 × 105 = 1260 ; kỳ vọng 10 × 100 = 1000
            var r = calc.calculate(VarianceInput.threeWay(d("12"), d("105"), d("100"), d("10")));

            assertThat(r.qtyVariance()).isEqualByComparingTo("2");
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("20");
            assertThat(r.priceVariance()).isEqualByComparingTo("5");
            assertThat(r.priceVariancePct()).isEqualByComparingTo("5");
            assertThat(r.amountVariance()).isEqualByComparingTo("260");
        }

        @Test
        void nothingReceivedYet_pctIsNullAndUnbounded() {
            var r = calc.calculate(VarianceInput.threeWay(d("5"), d("10"), d("10"), d("0")));

            assertThat(r.qtyVariance()).isEqualByComparingTo("5");
            assertThat(r.qtyVariancePct()).isNull();
            assertThat(r.qtyVarianceUnbounded()).isTrue();
        }

        @Test
        void partialDeliveries_summedFromManyGrnLines() {
            BigDecimal received = VarianceCalculator.sumQty(Arrays.asList(d("40"), d("35.5"), null, d("24.5")));
            var r = calc.calculate(VarianceInput.threeWay(d("100"), d("1"), d("1"), received));

            assertThat(received).isEqualByComparingTo("100");
            assertThat(r.isExactMatch()).isTrue();
        }

        @Test
        void missingReceivedQty_rejected() {
            assertThatThrownBy(() -> VarianceInput.threeWay(d("1"), d("1"), d("1"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("receivedQty");
        }
    }

    @Nested
    class TwoWay {

        @Test
        void comparesAgainstOrderedQty() {
            var r = calc.calculate(VarianceInput.twoWay(d("110"), d("1000"), d("1000"), d("100")));

            assertThat(r.referenceQty()).isEqualByComparingTo("100");
            assertThat(r.qtyVariance()).isEqualByComparingTo("10");
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("10");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void freeItemOnPo_priceVariancePctNull() {
            var r = calc.calculate(VarianceInput.threeWay(d("1"), d("500"), d("0"), d("1")));

            assertThat(r.priceVariance()).isEqualByComparingTo("500");
            assertThat(r.priceVariancePct()).isNull();
            assertThat(r.priceVarianceUnbounded()).isTrue();
        }

        @Test
        void freeItemBothSides_zeroPct() {
            var r = calc.calculate(VarianceInput.threeWay(d("1"), d("0"), d("0"), d("1")));
            assertThat(r.priceVariancePct()).isEqualByComparingTo("0");
            assertThat(r.priceVarianceUnbounded()).isFalse();
        }

        @Test
        void explicitInvoiceAmount_usedInsteadOfQtyTimesPrice() {
            // hóa đơn có chiết khấu dòng: 10 × 100 nhưng thành tiền 990
            var in = new VarianceInput(MatchType.THREE_WAY, d("10"), d("100"), d("990"),
                    d("100"), null, d("10"));
            var r = calc.calculate(in);

            assertThat(r.invoiceAmount()).isEqualByComparingTo("990");
            assertThat(r.amountVariance()).isEqualByComparingTo("-10");
        }

        @Test
        void fractionalQuantities_roundedHalfUp() {
            // 1 / 3 * 100 = 33.33333 → 33.3333
            var r = calc.calculate(VarianceInput.threeWay(d("4"), d("1"), d("1"), d("3")));
            assertThat(r.qtyVariancePct()).isEqualByComparingTo("33.3333");
            // 2 / 3 * 100 = 66.6666.. → 66.6667
            var r2 = calc.calculate(VarianceInput.threeWay(d("5"), d("1"), d("1"), d("3")));
            assertThat(r2.qtyVariancePct()).isEqualByComparingTo("66.6667");
        }

        @Test
        void scalesAreNormalized() {
            var r = calc.calculate(VarianceInput.threeWay(d("1"), d("1"), d("1"), d("1")));
            assertThat(r.qtyVariance().scale()).isEqualTo(4);
            assertThat(r.priceVariance().scale()).isEqualTo(4);
            assertThat(r.qtyVariancePct().scale()).isEqualTo(4);
        }

        @Test
        void invalidInputs_rejected() {
            assertThatThrownBy(() -> VarianceInput.threeWay(d("0"), d("1"), d("1"), d("1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VarianceInput.threeWay(d("1"), d("-1"), d("1"), d("1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VarianceInput.twoWay(d("1"), d("1"), d("1"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void sumQty_handlesNullAndEmpty() {
            assertThat(VarianceCalculator.sumQty(null)).isEqualByComparingTo("0");
            assertThat(VarianceCalculator.sumQty(List.of())).isEqualByComparingTo("0");
        }
    }
}
