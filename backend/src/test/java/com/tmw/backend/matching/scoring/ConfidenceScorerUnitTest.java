package com.tmw.backend.matching.scoring;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceScorerUnitTest {

    private ConfidenceWeightProperties defaultWeights;
    private ConfidenceScorer scorer;

    @BeforeEach
    void setUp() {
        defaultWeights = new ConfidenceWeightProperties();
        defaultWeights.validateWeights();
        scorer = new ConfidenceScorer(defaultWeights);
    }

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

    @Test
    void shouldScore1WhenAll5CriteriaMatchPerfectly() {
        UUID poId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        ConfidenceInput perfectInput = new ConfidenceInput(
                poId, poLineId, poId, poLineId,
                supplierId, supplierId,
                " item-dell-5540 ", "ITEM-DELL-5540",
                BigDecimal.ZERO, new BigDecimal("1.0000"),
                BigDecimal.ZERO, new BigDecimal("2.0000")
        );

        ConfidenceBreakdown breakdown = scorer.score(perfectInput);

        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.supplierScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.itemCodeScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.priceScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.quantityScore()).isEqualByComparingTo("1.0000");
        assertThat(breakdown.overallScore()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldApplyPartialScoresAndCustomConfiguredWeightsAccurately() {
        // Cấu hình trọng số tùy chỉnh: poRef=0.30, supplier=0.20, itemCode=0.20, price=0.15, quantity=0.15
        ConfidenceWeightProperties customWeights = new ConfidenceWeightProperties();
        customWeights.setPoReference(new BigDecimal("0.3000"));
        customWeights.setSupplier(new BigDecimal("0.2000"));
        customWeights.setItemCode(new BigDecimal("0.2000"));
        customWeights.setPrice(new BigDecimal("0.1500"));
        customWeights.setQuantity(new BigDecimal("0.1500"));
        customWeights.validateWeights();

        ConfidenceScorer customScorer = new ConfidenceScorer(customWeights);

        UUID supplierId = UUID.randomUUID();
        ConfidenceInput partialInput = new ConfidenceInput(
                null, null, UUID.randomUUID(), UUID.randomUUID(), // Không có PO ref -> 0.5000
                supplierId, supplierId,                           // Khớp supplier -> 1.0000
                "ITEM-001-HCM", "ITEM-001",                       // Khớp một phần mã hàng -> 0.5000
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), // Chạm biên ngưỡng giá -> 0.7000
                new BigDecimal("0.0000"), new BigDecimal("2.0000")  // Khớp số lượng -> 1.0000
        );

        ConfidenceBreakdown breakdown = customScorer.score(partialInput);

        // Kỳ vọng: 0.5*0.30 + 1.0*0.20 + 0.5*0.20 + 0.7*0.15 + 1.0*0.15 = 0.15 + 0.20 + 0.10 + 0.105 + 0.15 = 0.7050
        assertThat(breakdown.poReferenceScore()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.itemCodeScore()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.priceScore()).isEqualByComparingTo("0.7000");
        assertThat(breakdown.overallScore()).isEqualByComparingTo("0.7050");

        BigDecimal aggregated = customScorer.aggregateOverall(List.of(breakdown));
        assertThat(aggregated).isEqualByComparingTo("0.7050");
    }

    @Test
    void shouldPenalizeVarianceOutsideToleranceBelow07000() {
        UUID supplierId = UUID.randomUUID();
        ConfidenceInput outOfToleranceInput = new ConfidenceInput(
                null, null, UUID.randomUUID(), UUID.randomUUID(),
                supplierId, supplierId,
                "ITEM-A", "ITEM-A",
                new BigDecimal("50.5000"), new BigDecimal("1.0000"), // Lệch 50.5% trên ngưỡng 1% -> 0.3500
                new BigDecimal("100.0000"), new BigDecimal("2.0000") // Lệch 100% -> 0.0000
        );

        ConfidenceBreakdown breakdown = scorer.score(outOfToleranceInput);

        assertThat(breakdown.priceScore()).isEqualByComparingTo("0.3500");
        assertThat(breakdown.quantityScore()).isEqualByComparingTo("0.0000");
    }
}