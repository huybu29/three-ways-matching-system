package com.tmw.backend.matching.strategy;

import com.tmw.backend.matching.api.MatchType;
import com.tmw.backend.matching.scoring.ConfidenceBreakdown;
import com.tmw.backend.matching.variance.VarianceResult;

import java.math.BigDecimal;
import java.util.UUID;

public record ProposedMatchLine(
        UUID invoiceLineId,
        UUID poId,
        UUID poLineId,
        UUID grnLineId,
        MatchType matchType,
        BigDecimal matchedQty,
        BigDecimal invoiceUnitPrice,
        BigDecimal poUnitPrice,
        VarianceResult variance,
        boolean withinTolerance,
        ConfidenceBreakdown confidence
) {
}