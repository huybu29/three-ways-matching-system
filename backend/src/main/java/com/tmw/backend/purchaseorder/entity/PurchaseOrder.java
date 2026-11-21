package com.tmw.backend.purchaseorder.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.purchaseorder.CurrencyCode;
import com.tmw.backend.purchaseorder.PoStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "purchase_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_order_po_number", columnNames = "po_number")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder extends BaseEntity {

    @Column(name = "po_number", nullable = false, length = 50)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private CurrencyCode currency = CurrencyCode.VND;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PoStatus status = PoStatus.OPEN;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public PurchaseOrder(String poNumber, UUID supplierId, LocalDate orderDate, CurrencyCode currency) {
        if (poNumber == null || poNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số đơn đặt hàng (po_number) không được để trống");
        }
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhà cung cấp (supplier_id) không được để trống");
        }
        if (orderDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày đặt hàng (order_date) không được để trống");
        }

        this.poNumber = poNumber.trim().toUpperCase(Locale.ROOT);
        this.supplierId = supplierId;
        this.orderDate = orderDate;
        this.currency = currency != null ? currency : CurrencyCode.VND;
        this.status = PoStatus.OPEN;
        this.totalAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(PurchaseOrderLine line) {
        if (line == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng đơn hàng không được null");
        }
        boolean duplicateLineNo = lines.stream()
                .anyMatch(existing -> existing.getLineNo() != null && existing.getLineNo().equals(line.getLineNo()));
        if (duplicateLineNo) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trùng số thứ tự dòng (line_no = " + line.getLineNo() + ") trong đơn hàng " + poNumber
            );
        }

        lines.add(line);
        line.assignTo(this);
        recalculateTotalAmount();
    }

    public void removeLine(PurchaseOrderLine line) {
        if (line != null && lines.remove(line)) {
            line.assignTo(null);
            recalculateTotalAmount();
        }
    }

    public void recalculateTotalAmount() {
        this.totalAmount = lines.stream()
                .map(PurchaseOrderLine::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public void changeStatus(PoStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái PO không được null");
        }
        this.status = newStatus;
    }
}