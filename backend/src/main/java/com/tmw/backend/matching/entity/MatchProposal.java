package com.tmw.backend.matching.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.matching.exception.InvalidStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.tmw.backend.matching.entity.DecimalScales.CONFIDENCE_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.MONEY_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.ROUNDING;

/**
 * Đề xuất đối chiếu cho MỘT hóa đơn (aggregate root của MatchLine).
 * id, version (@Version → ETag/If-Match), created_at kế thừa từ BaseEntity.
 *
 * <p>Entity chỉ chịu trách nhiệm đúng vòng đời + ghi nhận actor/thời điểm.
 * Kiểm tra SoD (actor != proposed_by / approved_by) do SoDGuard của BE-6 thực hiện
 * TRƯỚC khi gọi approve()/post(); giữ/hoàn hạn mức do CapacityAllocationService (BE-3).</p>
 */
@Entity
@Table(name = "match_proposals")
public class MatchProposal extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private MatchSource source;

    /** 0..1, chỉ có với AUTO. */
    @Column(name = "confidence", precision = 5, scale = CONFIDENCE_SCALE)
    private BigDecimal confidence;

    @Column(name = "total_matched_amount", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal totalMatchedAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);

    // ---- Dấu vết từng bước (phục vụ SoD + audit) ----
    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;
    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private UUID postedBy;
    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Lý do reject/cancel. */
    @Column(name = "reason", length = 1000)
    private String reason;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<MatchLine> lines = new ArrayList<>();

    protected MatchProposal() { /* JPA */ }

    private MatchProposal(UUID invoiceId, UUID supplierId, MatchSource source,
                          BigDecimal confidence, UUID proposedBy, Instant now) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
        this.supplierId = Objects.requireNonNull(supplierId, "supplierId");
        this.source = Objects.requireNonNull(source, "source");
        this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy");
        this.proposedAt = now;
        this.status = MatchStatus.PROPOSED;
        this.confidence = normalizeConfidence(source, confidence);
    }

    // ================================================================ factories

    public static MatchProposal auto(UUID invoiceId, UUID supplierId, BigDecimal confidence,
                                     UUID proposedBy, Clock clock) {
        return new MatchProposal(invoiceId, supplierId, MatchSource.AUTO, confidence, proposedBy, clock.instant());
    }

    public static MatchProposal manual(UUID invoiceId, UUID supplierId, UUID proposedBy, Clock clock) {
        return new MatchProposal(invoiceId, supplierId, MatchSource.MANUAL, null, proposedBy, clock.instant());
    }

    // ================================================================ lines

    public MatchLine addLine(MatchLine line) {
        Objects.requireNonNull(line, "line");
        if (status != MatchStatus.PROPOSED) {
            throw new IllegalStateException("Lines can only be added while PROPOSED, current: " + status);
        }
        line.attachTo(this, lines.size() + 1);
        lines.add(line);
        totalMatchedAmount = totalMatchedAmount.add(line.getMatchedAmount()).setScale(MONEY_SCALE, ROUNDING);
        return line;
    }

    public List<MatchLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** true nếu mọi dòng đã được đánh giá và đều nằm trong dung sai. */
    public boolean allLinesWithinTolerance() {
        return !lines.isEmpty() && lines.stream().allMatch(l -> Boolean.TRUE.equals(l.getWithinTolerance()));
    }

    // ================================================================ lifecycle

    public void approve(UUID actor, Clock clock) {
        requireLines();
        transition(MatchStatus.APPROVED);
        this.approvedBy = Objects.requireNonNull(actor, "actor");
        this.approvedAt = clock.instant();
    }

    public void post(UUID actor, Clock clock) {
        transition(MatchStatus.POSTED);
        this.postedBy = Objects.requireNonNull(actor, "actor");
        this.postedAt = clock.instant();
        lines.forEach(MatchLine::allocate);
    }

    public void reject(UUID actor, String reason, Clock clock) {
        requireReason(reason);
        transition(MatchStatus.REJECTED);
        this.rejectedBy = Objects.requireNonNull(actor, "actor");
        this.rejectedAt = clock.instant();
        this.reason = reason.trim();
        lines.forEach(MatchLine::release);
    }

    public void cancel(UUID actor, String reason, Clock clock) {
        transition(MatchStatus.CANCELLED);
        this.cancelledBy = Objects.requireNonNull(actor, "actor");
        this.cancelledAt = clock.instant();
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        lines.forEach(MatchLine::release);
    }

    private void transition(MatchStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(getId(), status, target);
        }
        this.status = target;
    }

    private void requireLines() {
        if (lines.isEmpty()) {
            throw new IllegalStateException("MatchProposal " + getId() + " has no lines");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private static BigDecimal normalizeConfidence(MatchSource source, BigDecimal c) {
        if (source == MatchSource.MANUAL) return null;
        if (c == null) throw new IllegalArgumentException("confidence is required for AUTO");
        if (c.compareTo(BigDecimal.ZERO) < 0 || c.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be in [0,1], got " + c);
        }
        return c.setScale(CONFIDENCE_SCALE, ROUNDING);
    }

    // ================================================================ getters

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getSupplierId() { return supplierId; }
    public MatchStatus getStatus() { return status; }
    public MatchSource getSource() { return source; }
    public BigDecimal getConfidence() { return confidence; }
    public BigDecimal getTotalMatchedAmount() { return totalMatchedAmount; }
    public UUID getProposedBy() { return proposedBy; }
    public Instant getProposedAt() { return proposedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public UUID getRejectedBy() { return rejectedBy; }
    public Instant getRejectedAt() { return rejectedAt; }
    public UUID getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getReason() { return reason; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MatchProposal other && getId() != null && getId().equals(other.getId()));
    }

    @Override
    public int hashCode() { return MatchProposal.class.hashCode(); }
}
