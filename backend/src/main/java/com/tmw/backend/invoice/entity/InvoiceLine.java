package com.tmw.backend.invoice.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "invoice_line", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoice_line_invoice_id_line_no", columnNames = {"invoice_id", "line_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine extends BaseEntity {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "po_line_id")
    private UUID poLineId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal qty;

    @Column(name = "matched_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal matchedQty = ZERO;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal vatRate = ZERO;

    public InvoiceLine(
            Integer lineNo,
            UUID poLineId,
            String itemCode,
            String description,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal vatRate
    ) {
        if (lineNo == null || lineNo <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số thứ tự dòng (line_no) phải lớn hơn 0");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã hàng hóa (item_code) không được để trống");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng hóa đơn (qty) phải lớn hơn 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn giá (unit_price) không được âm");
        }

        BigDecimal normalizedVat = (vatRate != null ? vatRate : BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
        if (normalizedVat.compareTo(BigDecimal.ZERO) < 0 || normalizedVat.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thuế suất (vat_rate) phải nằm trong khoảng từ 0 đến 1"
            );
        }

        this.lineNo = lineNo;
        this.poLineId = poLineId;
        this.itemCode = itemCode.trim().toUpperCase(Locale.ROOT);
        this.description = (description == null || description.isBlank()) ? null : description.trim();
        this.qty = qty.setScale(4, RoundingMode.HALF_UP);
        this.matchedQty = ZERO;
        this.unitPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
        this.vatRate = normalizedVat;
    }

    public void assignTo(Invoice invoice) {
        this.invoice = invoice;
    }

    public void linkPoLine(UUID poLineId) {
        this.poLineId = poLineId;
    }

    public BigDecimal getUnmatchedQty() {
        return qty.subtract(matchedQty);
    }

    public void addMatchedQty(BigDecimal delta) {
        BigDecimal normalizedDelta = requirePositive(delta, "matched_qty");
        BigDecimal nextMatched = this.matchedQty.add(normalizedDelta);
        if (nextMatched.compareTo(this.qty) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format(
                            "Số lượng đối chiếu (%s) vượt quá số lượng trên dòng hóa đơn (%s)",
                            nextMatched.toPlainString(),
                            this.qty.toPlainString()
                    )
            );
        }
        this.matchedQty = nextMatched;
    }

    public void releaseMatchedQty(BigDecimal delta) {
        BigDecimal normalizedDelta = requirePositive(delta, "matched_qty");
        BigDecimal nextMatched = this.matchedQty.subtract(normalizedDelta);
        if (nextMatched.compareTo(ZERO) < 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Số lượng giải phóng đối chiếu vượt quá matched_qty hiện tại"
            );
        }
        this.matchedQty = nextMatched;
    }

    public void resetMatchedQty() {
        this.matchedQty = ZERO;
    }

    public BigDecimal getLineAmount() {
        if (qty == null || unitPrice == null) {
            return ZERO;
        }
        return qty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal getVatAmount() {
        if (vatRate == null) {
            return ZERO;
        }
        return getLineAmount().multiply(vatRate).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường " + fieldName + " phải lớn hơn 0");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}