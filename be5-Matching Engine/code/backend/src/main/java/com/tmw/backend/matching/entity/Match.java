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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Đề xuất đối chiếu cho MỘT hóa đơn (aggregate root của MatchLine).
 *
 * <p>Quy ước trạng thái giữ chỗ (allocation_state) – Match và mọi MatchLine luôn cùng một giá trị:
 * <pre>
 *   NONE ──markReserved()──▶ RESERVED ──markAllocated()──▶ ALLOCATED
 *                               └────────markReleased()──▶ RELEASED
 * </pre>
 * Dòng mới thêm vào luôn ở NONE; chỉ được thêm dòng khi Match còn ở NONE
 * (tức là trước khi gọi CapacityAllocationService.reserve của BE-3).</p>
 *
 * <p>Ràng buộc "mỗi hóa đơn chỉ có tối đa 1 Match đang hoạt động (PROPOSED/APPROVED)" được
 * đảm bảo ở DB bằng partial unique index {@code uk_match_active_invoice}.</p>
 */
@Entity
@Table(name = "match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseEntity {

    private static final int SCORE_SCALE = 4;

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

    /** 0..1 với AUTO; luôn null với MANUAL (người dùng tự ghép nên không có điểm tin cậy). */
    @Column(name = "overall_confidence", precision = 5, scale = SCORE_SCALE)
    private BigDecimal overallConfidence;

    @Column(name = "tolerance_policy_id")
    private UUID tolerancePolicyId;

    @Column(name = "has_variance_exception", nullable = false)
    private boolean hasVarianceException = false;

    /** Tóm tắt do engine sinh ra (vd: "2/3 dòng lệch giá"). Không dùng để lưu lý do reject/cancel. */
    @Column(name = "summary_reason", length = 500)
    private String summaryReason;

    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    /** Lý do khi chuyển sang REJECTED hoặc CANCELLED (bắt buộc ở cả hai trường hợp). */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
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
        this.overallConfidence = normalizeConfidence(this.source, overallConfidence);
        this.proposedBy = proposedBy;
    }

    public List<MatchLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    // ================================================================ lines

    public void addLine(MatchLine line) {
        assertMutable();
        if (line == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng đối chiếu (MatchLine) không được null");
        }
        if (this.allocationState != AllocationState.NONE) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể thêm dòng khi Match đã giữ chỗ hạn mức (allocationState = " + allocationState + ")"
            );
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

    /** Engine ghi tóm tắt kết quả đối chiếu (không liên quan tới lý do reject/cancel). */
    public void updateSummaryReason(String summary) {
        assertMutable();
        this.summaryReason = (summary == null || summary.isBlank()) ? null : summary.trim();
    }

    // ================================================================ allocation

    /** Gọi sau khi BE-3 giữ chỗ thành công cho toàn bộ dòng: NONE → RESERVED (Match và mọi dòng). */
    public void markReserved() {
        assertMutable();
        requireAllocationState(AllocationState.NONE, AllocationState.RESERVED);
        lines.forEach(MatchLine::reserve);
        this.allocationState = AllocationState.RESERVED;
    }

    /** Gọi trong cùng transaction với lệnh Post (commitAllocation): RESERVED → ALLOCATED. */
    public void markAllocated() {
        assertMutable();
        requireAllocationState(AllocationState.RESERVED, AllocationState.ALLOCATED);
        lines.forEach(MatchLine::allocate);
        this.allocationState = AllocationState.ALLOCATED;
    }

    /** Gọi sau khi BE-3 hoàn hạn mức (reject/cancel): RESERVED → RELEASED. */
    public void markReleased() {
        assertMutable();
        requireAllocationState(AllocationState.RESERVED, AllocationState.RELEASED);
        lines.forEach(MatchLine::release);
        this.allocationState = AllocationState.RELEASED;
    }

    private void requireAllocationState(AllocationState expected, AllocationState target) {
        if (this.allocationState != expected) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Chỉ có thể chuyển hạn mức sang %s khi đang ở %s (hiện tại: %s)",
                            target, expected, allocationState)
            );
        }
    }

    // ================================================================ lifecycle

    /**
     * Chỉ được gọi thông qua MatchStateMachine (đã kiểm tra ma trận trạng thái, actor và reason).
     */
    public void applyTransition(MatchStatus targetStatus, UUID actorId, String reason, Instant at) {
        assertMutable();
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Trạng thái đích không được null");
        }
        if (at == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời điểm chuyển trạng thái không được null");
        }
        switch (targetStatus) {
            case APPROVED -> {
                this.approvedBy = actorId;
                this.approvedAt = at;
            }
            case POSTED -> {
                this.postedBy = actorId;
                this.postedAt = at;
            }
            case REJECTED, CANCELLED -> {
                if (reason == null || reason.isBlank()) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Bắt buộc nhập lý do khi chuyển Match sang " + targetStatus
                    );
                }
                this.rejectReason = reason.trim();
            }
            default -> { /* PROPOSED: không có dữ liệu đi kèm */ }
        }
        this.status = targetStatus;
    }

    /** Tiện cho unit test / code cũ: dùng thời điểm hiện tại. */
    public void applyTransition(MatchStatus targetStatus, UUID actorId, String reason) {
        applyTransition(targetStatus, actorId, reason, Instant.now());
    }

    public void assertMutable() {
        if (this.status != null && this.status.isImmutable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Match " + getId() + " đã ở trạng thái POSTED nên bất biến, không thể chỉnh sửa"
            );
        }
    }

    // ================================================================ mapping

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
                approvedAt,
                postedBy,
                postedAt,
                rejectReason,
                version,
                lineProposals
        );
    }

    private static BigDecimal normalizeConfidence(MatchSource source, BigDecimal score) {
        if (source == MatchSource.MANUAL) {
            if (score != null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Match MANUAL không có điểm tin cậy (overall_confidence phải để trống)"
                );
            }
            return null;
        }
        if (score == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Match AUTO bắt buộc có điểm tin cậy (overall_confidence)"
            );
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Điểm tin cậy (overall_confidence) phải nằm trong khoảng từ 0 đến 1"
            );
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
