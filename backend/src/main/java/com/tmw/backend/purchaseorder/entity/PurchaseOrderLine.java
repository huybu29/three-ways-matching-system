package com.tmw.backend.purchaseorder.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Entity
@Table(name = "po_line", uniqueConstraints = {
        @UniqueConstraint(name = "uk_po_line_po_id_line_no", columnNames = {"purchase_order_id", "line_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQty;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatRate;

    public PurchaseOrderLine(
            Integer lineNo,
            String itemCode,
            String description,
            BigDecimal orderedQty,
            BigDecimal unitPrice,
            BigDecimal vatRate
    ) {
        if (lineNo == null || lineNo <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số thứ tự dòng (line_no) phải lớn hơn 0");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã hàng hóa (item_code) không được để trống");
        }
        if (orderedQty == null || orderedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng đặt (ordered_qty) phải lớn hơn 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn giá (unit_price) không được âm");
        }

        BigDecimal normalizedVat = (vatRate != null ? vatRate : BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
        if (normalizedVat.compareTo(BigDecimal.ZERO) < 0 || normalizedVat.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thuế suất (vat_rate) phải nằm trong khoảng từ 0 đến 1 (ví dụ 0.10 cho 10%)"
            );
        }

        this.lineNo = lineNo;
        this.itemCode = itemCode.trim().toUpperCase(Locale.ROOT);
        this.description = (description == null || description.isBlank()) ? null : description.trim();
        this.orderedQty = orderedQty.setScale(4, RoundingMode.HALF_UP);
        this.unitPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
        this.vatRate = normalizedVat;
    }

    public void assignTo(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public BigDecimal getLineAmount() {
        if (orderedQty == null || unitPrice == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return orderedQty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal getVatAmount() {
        return getLineAmount().multiply(vatRate).setScale(4, RoundingMode.HALF_UP);
    }
}