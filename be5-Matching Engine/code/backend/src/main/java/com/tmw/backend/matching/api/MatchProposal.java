package com.tmw.backend.matching.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot chỉ-đọc của một Match, dùng cho các module khác (BE-6 SoDGuard / Posting, BE-7 ...).
 *
 * @param overallConfidence null với Match MANUAL
 * @param rejectReason      lý do khi Match ở REJECTED hoặc CANCELLED
 */
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
        Instant approvedAt,
        UUID postedBy,
        Instant postedAt,
        String rejectReason,
        Long version,
        List<MatchLineProposal> lines
) {

    /**
     * Constructor cũ (không có approvedAt/postedAt/rejectReason) – giữ lại để code và test
     * hiện có của các module khác (vd. SoDGuardMatrixUnitTest của BE-6) không phải sửa.
     */
    public MatchProposal(
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
        this(id, invoiceId, status, source, allocationState, overallConfidence, tolerancePolicyId,
                hasVarianceException, summaryReason, proposedBy, approvedBy, null, postedBy, null, null,
                version, lines);
    }

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
