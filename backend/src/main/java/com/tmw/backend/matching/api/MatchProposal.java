package com.tmw.backend.matching.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MatchProposal(
        UUID id,
        UUID invoiceId,
        MatchStatus status,
        MatchSource source,
        AllocationState allocationState,
        BigDecimal overallConfidence,
        UUID tolerancePolicyId,
        boolean hasVarianceException,
        String summaryReason,
        UUID proposedBy,
        UUID approvedBy,
        UUID postedBy,
        Long version,
        List<MatchLineProposal> lines
) {
    public record MatchLineProposal(
            UUID id,
            UUID invoiceLineId,
            UUID poId,
            UUID poLineId,
            UUID grnLineId,
            BigDecimal matchedQty,
            BigDecimal invoiceUnitPrice,
            BigDecimal poUnitPrice,
            BigDecimal matchedAmount,
            BigDecimal qtyVariance,
            BigDecimal qtyVariancePct,
            BigDecimal priceVariance,
            BigDecimal priceVariancePct,
            BigDecimal confidenceScore,
            boolean withinTolerance
    ) {
    }
}