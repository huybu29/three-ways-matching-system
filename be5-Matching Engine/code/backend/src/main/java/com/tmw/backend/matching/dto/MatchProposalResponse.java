package com.tmw.backend.matching.dto;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchProposalResponse(
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
        int lineCount,
        int exceptionLineCount,
        BigDecimal totalMatchedAmount,
        Long version,
        List<MatchLineResponse> lines
) {

    public static MatchProposalResponse from(MatchProposal proposal) {
        if (proposal == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dữ liệu đề xuất đối chiếu");
        }

        List<MatchLineResponse> lineResponses = proposal.lines() == null
                ? List.of()
                : proposal.lines().stream()
                        .map(MatchLineResponse::from)
                        .toList();

        int exceptionCount = (int) lineResponses.stream()
                .filter(line -> !line.withinTolerance())
                .count();

        BigDecimal totalAmount = lineResponses.stream()
                .map(MatchLineResponse::matchedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);

        return new MatchProposalResponse(
                proposal.id(),
                proposal.invoiceId(),
                proposal.status(),
                proposal.source(),
                proposal.allocationState(),
                proposal.overallConfidence(),
                proposal.tolerancePolicyId(),
                proposal.hasVarianceException(),
                proposal.summaryReason(),
                proposal.proposedBy(),
                proposal.approvedBy(),
                proposal.approvedAt(),
                proposal.postedBy(),
                proposal.postedAt(),
                proposal.rejectReason(),
                lineResponses.size(),
                exceptionCount,
                totalAmount,
                proposal.version(),
                lineResponses
        );
    }

    /**
     * Trả về giá trị chuẩn cho HTTP Header ETag (ví dụ: "\"3\"").
     */
    public String toEtag() {
        return version != null ? "\"" + version + "\"" : null;
    }

    public record MatchLineResponse(
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
        public static MatchLineResponse from(MatchProposal.MatchLineProposal line) {
            return new MatchLineResponse(
                    line.id(),
                    line.invoiceLineId(),
                    line.poId(),
                    line.poLineId(),
                    line.grnLineId(),
                    line.matchedQty(),
                    line.invoiceUnitPrice(),
                    line.poUnitPrice(),
                    line.matchedAmount(),
                    line.qtyVariance(),
                    line.qtyVariancePct(),
                    line.priceVariance(),
                    line.priceVariancePct(),
                    line.confidenceScore(),
                    line.withinTolerance()
            );
        }
    }
}