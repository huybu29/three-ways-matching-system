package com.tmw.backend.matching.variance;

import java.math.BigDecimal;

public record VarianceResult(
        BigDecimal referenceQty,
        BigDecimal qtyVariance,
        BigDecimal qtyVariancePct,
        BigDecimal priceVariance,
        BigDecimal priceVariancePct,
        BigDecimal amountVariance
) {
}