package com.tmw.backend.matching.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MatchStatus status = MatchStatus.PROPOSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MatchSource source = MatchSource.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_state", nullable = false, length = 20)
    private AllocationState allocationState = AllocationState.NONE;

    @Column(name = "overall_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal overallConfidence = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Column(name = "tolerance_policy_id")
    private UUID tolerancePolicyId;

    @Column(name = "has_variance_exception", nullable = false)
    private boolean hasVarianceException = false;

    @Column(name = "summary_reason", length = 500)
    private String summaryReason;

    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchLine> lines = new ArrayList<>();

    public Match(
            UUID invoiceId,
            MatchSource source,
            UUID tolerancePolicyId,
            BigDecimal overallConfidence,
            UUID proposedBy
    ) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoice_id không được để trống");
        }
        if (proposedBy == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "proposed_by không được để trống");
        }
        this.invoiceId = invoiceId;
        this.source = source != null ? source : MatchSource.AUTO;
        this.status = MatchStatus.PROPOSED;
        this.allocationState = AllocationState.NONE;
        this.tolerancePolicyId = tolerancePolicyId;
        this.overallConfidence = normalizeScore(overallConfidence);
        this.proposedBy = proposedBy;
    }

    public List<MatchLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(MatchLine line) {
        assertMutable();
        if (line == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng đối chiếu (MatchLine) không được null");
        }
        boolean duplicatePair = lines.stream().anyMatch(existing ->
                existing.getInvoiceLineId().equals(line.getInvoiceLineId())
                        && existing.getPoLineId().equals(line.getPoLineId())
        );
        if (duplicatePair) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    String.format(
                            "Trùng cặp dòng đối chiếu (invoice_line_id=%s, po_line_id=%s)",
                            line.getInvoiceLineId(), line.getPoLineId()
                    )
            );
        }

        lines.add(line);
        line.attachTo(this, lines.size());
        recalculateSummary();
    }

    public void recalculateSummary() {
        assertMutable();
        this.hasVarianceException = lines.stream()
                .anyMatch(l -> Boolean.FALSE.equals(l.getWithinTolerance()));
    }

    public void markReserved() {
        assertMutable();
        if (this.allocationState != AllocationState.NONE) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể giữ chỗ hạn mức khi allocationState đang ở NONE"
            );
        }
        this.allocationState = AllocationState.RESERVED;
    }

    public void markAllocated() {
        assertMutable();
        if (this.allocationState != AllocationState.RESERVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể chốt phân bổ (ALLOCATED) khi đang giữ chỗ (RESERVED)"
            );
        }
        for (MatchLine line : lines) {
            line.allocate();
        }
        this.allocationState = AllocationState.ALLOCATED;
    }

    public void markReleased() {
        assertMutable();
        if (this.allocationState != AllocationState.RESERVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể giải phóng (RELEASED) khi đang giữ chỗ (RESERVED)"
            );
        }
        for (MatchLine line : lines) {
            line.release();
        }
        this.allocationState = AllocationState.RELEASED;
    }

    /**
     * Chỉ được gọi thông qua MatchStateMachine để đảm bảo tuân thủ ma trận trạng thái.
     */
    public void applyTransition(MatchStatus targetStatus, UUID actorId, String reason) {
        assertMutable();
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Trạng thái đích không được null");
        }
        if (targetStatus == MatchStatus.APPROVED) {
            this.approvedBy = actorId;
        } else if (targetStatus == MatchStatus.POSTED) {
            this.postedBy = actorId;
        }
        this.status = targetStatus;
        if (reason != null && !reason.isBlank()) {
            this.summaryReason = reason.trim();
        }
    }

    public void assertMutable() {
        if (this.status != null && this.status.isImmutable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Match " + getId() + " đã ở trạng thái POSTED nên bất biến, không thể chỉnh sửa"
            );
        }
    }

    public MatchProposal toProposal() {
        List<MatchProposal.MatchLineProposal> lineProposals = lines.stream()
                .map(l -> new MatchProposal.MatchLineProposal(
                        l.getId(),
                        l.getInvoiceLineId(),
                        l.getPoId(),
                        l.getPoLineId(),
                        l.getGrnLineId(),
                        l.getMatchedQty(),
                        l.getInvoiceUnitPrice(),
                        l.getPoUnitPrice(),
                        l.getMatchedAmount(),
                        l.getQtyVariance(),
                        l.getQtyVariancePct(),
                        l.getPriceVariance(),
                        l.getPriceVariancePct(),
                        l.getConfidenceScore(),
                        Boolean.TRUE.equals(l.getWithinTolerance())
                ))
                .toList();

        return new MatchProposal(
                getId(),
                invoiceId,
                status,
                source,
                allocationState,
                overallConfidence,
                tolerancePolicyId,
                hasVarianceException,
                summaryReason,
                proposedBy,
                approvedBy,
                postedBy,
                version,
                lineProposals
        );
    }

    private static BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Điểm tin cậy (overall_confidence) phải nằm trong khoảng từ 0 đến 1"
            );
        }
        return score.setScale(4, RoundingMode.HALF_UP);
    }
}