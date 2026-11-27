package com.tmw.backend.matching.scoring;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceScorerUnitTest {

    private ConfidenceScorer scorer;

    @BeforeEach
    void setUp() {
        ConfidenceWeightProperties defaultWeights = new ConfidenceWeightProperties();
        defaultWeights.validateWeights();
        scorer = new ConfidenceScorer(defaultWeights);
    }

    // ------------------------------------------------------------------ cấu hình trọng số

    @Test
    void shouldRejectInvalidWeightConfigurationsAtStartup() {
        ConfidenceWeightProperties badSum = new ConfidenceWeightProperties();
        badSum.setPoReference(new BigDecimal("0.3000")); // Tổng thành 1.0500

        assertThatThrownBy(badSum::validateWeights)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        ConfidenceWeightProperties negativeWeight = new ConfidenceWeightProperties();
        negativeWeight.setPrice(new BigDecimal("-0.1000"));
        negativeWeight.setQuantity(new BigDecimal("0.4000"));

        assertThatThrownBy(negativeWeight::validateWeights)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ------------------------------------------------------------------ điểm tổng

    @Test
    void shouldScore1WhenAll5CriteriaMatchPerfectly() {
        UUID poLineId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        ConfidenceBreakdown breakdown = scorer.score(ConfidenceInput.builder()
                .invoicePoReference("PO-2026-0012").candidatePoNumber("PO-2026-0012")
                .invoiceLinePoLineIdHint(poLineId).candidatePoLineId(poLineId)
                .invoiceSupplierId(supplierId).poSupplierId(supplierId)
                .invoiceItemCode(" item-dell-5540 ").poItemCode("ITEM-DELL-5540")
                .priceVariancePct(BigDecimal.ZERO).priceTolerancePct(new BigDecimal("1.0000"))
                .qtyVariancePct(BigDecimal.ZERO).qtyTolerancePct(new BigDecimal("2.0000"))
                .build());

        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.supplierScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.itemCodeScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.priceScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.quantityScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.overallScore()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldApplyPartialScoresAndCustomConfiguredWeightsAccurately() {
        // Trọng số tùy chỉnh: poRef=0.30, supplier=0.20, itemCode=0.20, price=0.15, quantity=0.15
        ConfidenceWeightProperties customWeights = new ConfidenceWeightProperties();
        customWeights.setPoReference(new BigDecimal("0.3000"));
        customWeights.setSupplier(new BigDecimal("0.2000"));
        customWeights.setItemCode(new BigDecimal("0.2000"));
        customWeights.setPrice(new BigDecimal("0.1500"));
        customWeights.setQuantity(new BigDecimal("0.1500"));
        customWeights.validateWeights();
        ConfidenceScorer customScorer = new ConfidenceScorer(customWeights);

        UUID supplierId = UUID.randomUUID();
        ConfidenceBreakdown breakdown = customScorer.score(ConfidenceInput.builder()
                .candidatePoNumber("PO-2026-0099").candidatePoId(UUID.randomUUID())  // không có PO ref -> 0.5
                .invoiceSupplierId(supplierId).poSupplierId(supplierId)               // khớp NCC -> 1.0
                .invoiceItemCode("ITEM-001-HCM").poItemCode("ITEM-001")               // khớp một phần -> 0.5
                .priceVariancePct(new BigDecimal("1.0000")).priceTolerancePct(new BigDecimal("1.0000")) // chạm biên -> 0.7
                .qtyVariancePct(BigDecimal.ZERO).qtyTolerancePct(new BigDecimal("2.0000"))              // đủ SL -> 1.0
                .build());

        // 0.5*0.30 + 1.0*0.20 + 0.5*0.20 + 0.7*0.15 + 1.0*0.15 = 0.7050
        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.itemCodeScore()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.priceScore()).isEqualByComparingTo("0.7000");
        assertThat(breakdown.overallScore()).isEqualByComparingTo("0.7050");
        assertThat(customScorer.aggregateOverall(List.of(breakdown))).isEqualByComparingTo("0.7050");
    }

    @Test
    void shouldPenalizeVarianceOutsideToleranceBelow07000() {
        ConfidenceBreakdown breakdown = scorer.score(ConfidenceInput.builder()
                .invoiceItemCode("ITEM-A").poItemCode("ITEM-A")
                .priceVariancePct(new BigDecimal("50.5000")).priceTolerancePct(new BigDecimal("1.0000")) // -> 0.35
                .qtyVariancePct(new BigDecimal("100.0000")).qtyTolerancePct(new BigDecimal("2.0000"))    // -> 0
                .build());

        assertThat(breakdown.priceScore()).isEqualByComparingTo("0.3500");
        assertThat(breakdown.quantityScore()).isEqualByComparingTo("0.0000");
    }

    // ------------------------------------------------------------------ tiêu chí số PO

    @Test
    void shouldMatchPoReferenceAsStringIgnoringCaseAndSurroundingSpaces() {
        assertThat(poReferenceScore(" po-2026-0012 ", "PO-2026-0012")).isEqualByComparingTo("1.0000");
        assertThat(poReferenceScore("PO-2026-0012", "PO-2026-0013")).isEqualByComparingTo("0.0000");
        // Hóa đơn không ghi số PO (null hoặc chỉ có khoảng trắng) -> 0.5000
        assertThat(poReferenceScore(null, "PO-2026-0012")).isEqualByComparingTo("0.5000");
        assertThat(poReferenceScore("   ", "PO-2026-0012")).isEqualByComparingTo("0.5000");
    }

    @Test
    void shouldScore1WhenInvoiceLinePointsToCandidatePoLineEvenIfHeaderRefMissing() {
        UUID poLineId = UUID.randomUUID();
        ConfidenceBreakdown breakdown = scorer.score(ConfidenceInput.builder()
                .invoiceLinePoLineIdHint(poLineId).candidatePoLineId(poLineId).candidatePoNumber("PO-2026-0012")
                .build());
        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldUseInvoiceHeaderPurchaseOrderIdHint() {
        UUID poId = UUID.randomUUID();
        assertThat(scorer.score(ConfidenceInput.builder()
                .invoicePoIdHint(poId).candidatePoId(poId).build()).poReferenceScore())
                .isEqualByComparingTo("1.0000");
        // Có gợi ý PO nhưng ứng viên thuộc PO khác -> 0
        assertThat(scorer.score(ConfidenceInput.builder()
                .invoicePoIdHint(poId).candidatePoId(UUID.randomUUID()).build()).poReferenceScore())
                .isEqualByComparingTo("0.0000");
    }

    // ------------------------------------------------------------------ an toàn với dữ liệu thiếu

    @Test
    void shouldNotFailOnAllNullInputAndGiveOnlyNeutralPoReferenceScore() {
        ConfidenceBreakdown breakdown = scorer.score(ConfidenceInput.builder().build());

        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.supplierScore()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.itemCodeScore()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.priceScore()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.quantityScore()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.overallScore()).isEqualByComparingTo("0.1250"); // 0.5 * 0.25
    }

    @Test
    void shouldAlwaysKeepEveryScoreWithin0And1ForRandomInputs() {
        Random random = new Random(20261127L);
        UUID supplier = UUID.randomUUID();
        for (int i = 0; i < 500; i++) {
            ConfidenceBreakdown b = scorer.score(ConfidenceInput.builder()
                    .invoicePoReference(random.nextBoolean() ? "PO-" + random.nextInt(5) : null)
                    .candidatePoNumber("PO-" + random.nextInt(5))
                    .invoiceSupplierId(supplier).poSupplierId(random.nextBoolean() ? supplier : UUID.randomUUID())
                    .invoiceItemCode("ITEM-" + random.nextInt(3)).poItemCode("ITEM-" + random.nextInt(3))
                    .priceVariancePct(BigDecimal.valueOf(random.nextInt(30000), 2))   // 0..300%
                    .priceTolerancePct(BigDecimal.valueOf(random.nextInt(1000), 2))  // 0..10%
                    .qtyVariancePct(BigDecimal.valueOf(random.nextInt(10001), 2))    // 0..100%
                    .qtyTolerancePct(BigDecimal.valueOf(random.nextInt(1000), 2))
                    .build());
            for (BigDecimal s : List.of(b.poReferenceScore(), b.supplierScore(), b.itemCodeScore(),
                    b.priceScore(), b.quantityScore(), b.overallScore())) {
                assertThat(s.signum() >= 0 && s.compareTo(BigDecimal.ONE) <= 0).isTrue();
            }
        }
    }

    @Test
    void shouldRejectNullInput() {
        assertThatThrownBy(() -> scorer.score(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private BigDecimal poReferenceScore(String invoiceRef, String candidatePoNumber) {
        return scorer.score(ConfidenceInput.builder()
                .invoicePoReference(invoiceRef)
                .candidatePoNumber(candidatePoNumber)
                .candidatePoLineId(UUID.randomUUID())
                .build()).poReferenceScore();
    }
}
