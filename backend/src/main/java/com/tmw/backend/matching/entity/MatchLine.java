package com.tmw.backend.matching.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.matching.variance.VarianceResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import static com.tmw.backend.matching.entity.DecimalScales.MONEY_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.QTY_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.ROUNDING;

/**
 * Một dòng ghép: dòng hóa đơn ↔ dòng PO (↔ dòng GRN nếu 3-way).
 *
 * <p>Ghép 1-nhiều / nhiều-1 được biểu diễn bằng NHIỀU MatchLine trong cùng một proposal:
 * <ul>
 *   <li>1 dòng hóa đơn chia cho 2 dòng PO → 2 MatchLine cùng invoice_line_id</li>
 *   <li>2 dòng hóa đơn gom vào 1 dòng PO → 2 MatchLine cùng po_line_id</li>
 * </ul>
 */
@Entity
@Table(name = "match_lines")
public class MatchLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_proposal_id", nullable = false)
    private MatchProposal proposal;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    // ---- Tham chiếu chứng từ (FK sang bảng của BE-3 / BE-4) ----
    @Column(name = "invoice_line_id", nullable = false)
    private UUID invoiceLineId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    /** null với TWO_WAY. */
    @Column(name = "grn_line_id")
    private UUID grnLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    private MatchType matchType;

    // ---- Số lượng / giá được ghép ----
    /** Số lượng giữ chỗ trên Capacity của dòng PO. */
    @Column(name = "matched_qty", nullable = false, precision = 19, scale = QTY_SCALE)
    private BigDecimal matchedQty;

    @Column(name = "invoice_unit_price", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal invoiceUnitPrice;

    @Column(name = "po_unit_price", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal poUnitPrice;

    /** Thành tiền giữ chỗ (chưa VAT) = matchedQty × invoiceUnitPrice. */
    @Column(name = "matched_amount", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal matchedAmount;

    // ---- Kết quả tính độ lệch (VarianceCalculator) ----
    @Column(name = "reference_qty", precision = 19, scale = QTY_SCALE)
    private BigDecimal referenceQty;

    @Column(name = "qty_variance", precision = 19, scale = QTY_SCALE)
    private BigDecimal qtyVariance;

    @Column(name = "qty_variance_pct", precision = 9, scale = DecimalScales.PCT_SCALE)
    private BigDecimal qtyVariancePct;

    @Column(name = "price_variance", precision = 19, scale = MONEY_SCALE)
    private BigDecimal priceVariance;

    @Column(name = "price_variance_pct", precision = 9, scale = DecimalScales.PCT_SCALE)
    private BigDecimal priceVariancePct;

    @Column(name = "amount_variance", precision = 19, scale = MONEY_SCALE)
    private BigDecimal amountVariance;

    /** null = chưa đánh giá dung sai (gắn ở GĐ3 cùng BE-2). */
    @Column(name = "within_tolerance")
    private Boolean withinTolerance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MatchLineStatus status;

    protected MatchLine() { /* JPA */ }

    public MatchLine(UUID invoiceLineId, UUID poId, UUID poLineId, UUID grnLineId,
                     MatchType matchType, BigDecimal matchedQty,
                     BigDecimal invoiceUnitPrice, BigDecimal poUnitPrice) {
        this.invoiceLineId = Objects.requireNonNull(invoiceLineId, "invoiceLineId");
        this.poId = Objects.requireNonNull(poId, "poId");
        this.poLineId = Objects.requireNonNull(poLineId, "poLineId");
        this.matchType = Objects.requireNonNull(matchType, "matchType");
        if (matchType == MatchType.THREE_WAY && grnLineId == null) {
            throw new IllegalArgumentException("grnLineId is required for THREE_WAY match line");
        }
        this.grnLineId = grnLineId;

        if (matchedQty == null || matchedQty.signum() <= 0) {
            throw new IllegalArgumentException("matchedQty must be > 0");
        }
        if (invoiceUnitPrice == null || invoiceUnitPrice.signum() < 0
                || poUnitPrice == null || poUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("unit prices must be >= 0");
        }
        this.matchedQty = matchedQty.setScale(QTY_SCALE, ROUNDING);
        this.invoiceUnitPrice = invoiceUnitPrice.setScale(MONEY_SCALE, ROUNDING);
        this.poUnitPrice = poUnitPrice.setScale(MONEY_SCALE, ROUNDING);
        this.matchedAmount = this.matchedQty.multiply(this.invoiceUnitPrice).setScale(MONEY_SCALE, ROUNDING);
        this.status = MatchLineStatus.RESERVED;
    }

    /** Gán kết quả từ VarianceCalculator. Reset within_tolerance vì số liệu đã đổi. */
    public void applyVariance(VarianceResult r) {
        Objects.requireNonNull(r, "variance result");
        this.referenceQty = r.referenceQty();
        this.qtyVariance = r.qtyVariance();
        this.qtyVariancePct = r.qtyVariancePct();
        this.priceVariance = r.priceVariance();
        this.priceVariancePct = r.priceVariancePct();
        this.amountVariance = r.amountVariance();
        this.withinTolerance = null;
    }

    /** Gọi ở GĐ3 sau khi ToleranceEvaluationService đánh giá. */
    public void markTolerance(boolean within) {
        this.withinTolerance = within;
    }

    // ---- package-private: chỉ MatchProposal được đổi trạng thái dòng ----
    void attachTo(MatchProposal proposal, int lineNo) {
        this.proposal = proposal;
        this.lineNo = lineNo;
    }

    void release() {
        if (status == MatchLineStatus.RESERVED) status = MatchLineStatus.RELEASED;
    }

    void allocate() {
        if (status != MatchLineStatus.RESERVED) {
            throw new IllegalStateException("Only RESERVED line can be allocated, line " + getId() + " is " + status);
        }
        status = MatchLineStatus.ALLOCATED;
    }

    // ---- getters ----
    public MatchProposal getProposal() { return proposal; }
    public int getLineNo() { return lineNo; }
    public UUID getInvoiceLineId() { return invoiceLineId; }
    public UUID getPoId() { return poId; }
    public UUID getPoLineId() { return poLineId; }
    public UUID getGrnLineId() { return grnLineId; }
    public MatchType getMatchType() { return matchType; }
    public BigDecimal getMatchedQty() { return matchedQty; }
    public BigDecimal getInvoiceUnitPrice() { return invoiceUnitPrice; }
    public BigDecimal getPoUnitPrice() { return poUnitPrice; }
    public BigDecimal getMatchedAmount() { return matchedAmount; }
    public BigDecimal getReferenceQty() { return referenceQty; }
    public BigDecimal getQtyVariance() { return qtyVariance; }
    public BigDecimal getQtyVariancePct() { return qtyVariancePct; }
    public BigDecimal getPriceVariance() { return priceVariance; }
    public BigDecimal getPriceVariancePct() { return priceVariancePct; }
    public BigDecimal getAmountVariance() { return amountVariance; }
    public Boolean getWithinTolerance() { return withinTolerance; }
    public MatchLineStatus getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MatchLine other && getId() != null && getId().equals(other.getId()));
    }

    @Override
    public int hashCode() { return MatchLine.class.hashCode(); }
}
