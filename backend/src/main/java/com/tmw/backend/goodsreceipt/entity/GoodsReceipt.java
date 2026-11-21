package com.tmw.backend.goodsreceipt.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_goods_receipt_grn_number", columnNames = "grn_number")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceipt extends BaseEntity {

    @Column(name = "grn_number", nullable = false, length = 50)
    private String grnNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    public GoodsReceipt(
            String grnNumber,
            UUID purchaseOrderId,
            LocalDate receivedDate,
            String notes,
            UUID createdBy
    ) {
        if (grnNumber == null || grnNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số phiếu nhập kho (grn_number) không được để trống");
        }
        if (purchaseOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn đặt hàng (purchase_order_id) không được để trống");
        }
        if (receivedDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày nhập kho (received_date) không được để trống");
        }

        this.grnNumber = grnNumber.trim().toUpperCase(Locale.ROOT);
        this.purchaseOrderId = purchaseOrderId;
        this.receivedDate = receivedDate;
        this.notes = (notes == null || notes.isBlank()) ? null : notes.trim();
        this.createdBy = createdBy;
    }

    public List<GoodsReceiptLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Thêm dòng nhập kho kèm kiểm tra bất biến:
     * 1. Dòng PO phải thuộc đúng PurchaseOrder của phiếu GRN này.
     * 2. Không được trùng cặp (goods_receipt_id, po_line_id).
     */
    public void addLine(GoodsReceiptLine line, UUID linePurchaseOrderId) {
        if (line == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng nhập kho không được null");
        }
        if (linePurchaseOrderId == null || !this.purchaseOrderId.equals(linePurchaseOrderId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format(
                            "Dòng PO (%s) không thuộc đơn đặt hàng (%s) của phiếu nhập kho %s",
                            line.getPoLineId(), this.purchaseOrderId, this.grnNumber
                    )
            );
        }

        boolean duplicatePoLine = lines.stream()
                .anyMatch(existing -> existing.getPoLineId().equals(line.getPoLineId()));
        if (duplicatePoLine) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Dòng PO (" + line.getPoLineId() + ") đã tồn tại trong phiếu nhập kho " + this.grnNumber
            );
        }

        lines.add(line);
        line.assignTo(this);
    }

    public void removeLine(GoodsReceiptLine line) {
        if (line != null && lines.remove(line)) {
            line.assignTo(null);
        }
    }
}