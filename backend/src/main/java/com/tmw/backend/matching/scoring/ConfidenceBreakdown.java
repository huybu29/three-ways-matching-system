package com.tmw.backend.matching.scoring;

import java.math.BigDecimal;

public record ConfidenceBreakdown(
        BigDecimal poReferenceScore,
        BigDecimal supplierScore,
        BigDecimal itemCodeScore,
        BigDecimal priceScore,
        BigDecimal quantityScore,
        BigDecimal overallScore
) {
}