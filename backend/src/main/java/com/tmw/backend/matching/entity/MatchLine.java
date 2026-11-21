package com.tmw.backend.matching.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.variance.VarianceResult;
import com.tmw.backend.matching.api.MatchType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

import static com.tmw.backend.common.decimal.DecimalScales.*;

@Entity
@Table(name = "match_line", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_match_line_match_inv_line_po_line",
                columnNames = {"match_id", "invoice_line_id", "po_line_id"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "invoice_line_id", nullable = false)
    private UUID invoiceLineId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    @Column(name = "grn_line_id")
    private UUID grnLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    private MatchType matchType;

    @Column(name = "matched_qty", nullable = false, precision = 19, scale = QTY_SCALE)
    private BigDecimal matchedQty;

    @Column(name = "invoice_unit_price", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal invoiceUnitPrice;

    @Column(name = "po_unit_price", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal poUnitPrice;

    @Column(name = "matched_amount", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal matchedAmount;

    @Column(name = "reference_qty", precision = 19, scale = QTY_SCALE)
    private BigDecimal referenceQty;

    @Column(name = "qty_variance", precision = 19, scale = QTY_SCALE)
    private BigDecimal qtyVariance;

    @Column(name = "qty_variance_pct", precision = 19, scale = PCT_SCALE)
    private BigDecimal qtyVariancePct;

    @Column(name = "price_variance", precision = 19, scale = MONEY_SCALE)
    private BigDecimal priceVariance;

    @Column(name = "price_variance_pct", precision = 19, scale = PCT_SCALE)
    private BigDecimal priceVariancePct;

    @Column(name = "amount_variance", precision = 19, scale = MONEY_SCALE)
    private BigDecimal amountVariance;

    @Column(name = "within_tolerance")
    private Boolean withinTolerance;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_state", nullable = false, length = 16)
    private AllocationState allocationState;

    public MatchLine(
            UUID invoiceLineId,
            UUID poId,
            UUID poLineId,
            UUID grnLineId,
            MatchType matchType,
            BigDecimal matchedQty,
            BigDecimal invoiceUnitPrice,
            BigDecimal poUnitPrice
    ) {
        if (invoiceLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceLineId không được để trống");
        }
        if (poId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "poId không được để trống");
        }
        if (poLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "poLineId không được để trống");
        }
        if (matchType == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "matchType không được để trống");
        }
        if (matchType == MatchType.THREE_WAY && grnLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đối chiếu 3 chiều (THREE_WAY) bắt buộc có grnLineId");
        }
        if (matchedQty == null || matchedQty.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng đối chiếu (matchedQty) phải lớn hơn 0");
        }
        if (invoiceUnitPrice == null || invoiceUnitPrice.signum() < 0 || poUnitPrice == null || poUnitPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn giá hóa đơn và đơn giá PO không được âm");
        }

        this.invoiceLineId = invoiceLineId;
        this.poId = poId;
        this.poLineId = poLineId;
        this.grnLineId = grnLineId;
        this.matchType = matchType;
        this.matchedQty = matchedQty.setScale(QTY_SCALE, ROUNDING);
        this.invoiceUnitPrice = invoiceUnitPrice.setScale(MONEY_SCALE, ROUNDING);
        this.poUnitPrice = poUnitPrice.setScale(MONEY_SCALE, ROUNDING);
        this.matchedAmount = this.matchedQty.multiply(this.invoiceUnitPrice).setScale(MONEY_SCALE, ROUNDING);
        this.allocationState = AllocationState.RESERVED;
    }

    public void applyVariance(VarianceResult r, boolean withinTolerance) {
        if (r == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Kết quả tính chênh lệch (VarianceResult) không được null");
        }
        this.referenceQty = r.referenceQty();
        this.qtyVariance = r.qtyVariance();
        this.qtyVariancePct = r.qtyVariancePct();
        this.priceVariance = r.priceVariance();
        this.priceVariancePct = r.priceVariancePct();
        this.amountVariance = r.amountVariance();
        this.withinTolerance = withinTolerance;
    }

    public void assignConfidenceScore(BigDecimal score) {
        if (score != null) {
            this.confidenceScore = score.setScale(4, ROUNDING);
        }
    }

    void attachTo(Match match, int lineNo) {
        this.match = match;
        this.lineNo = lineNo;
    }

    void release() {
        if (allocationState == AllocationState.RESERVED) {
            allocationState = AllocationState.RELEASED;
        }
    }

    void allocate() {
        if (allocationState != AllocationState.RESERVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ dòng ở trạng thái RESERVED mới được chuyển sang ALLOCATED, dòng " + getId() + " hiện là " + allocationState
            );
        }
        allocationState = AllocationState.ALLOCATED;
    }
}