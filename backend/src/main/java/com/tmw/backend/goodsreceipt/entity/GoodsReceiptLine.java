package com.tmw.backend.goodsreceipt.entity;

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
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_line", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_goods_receipt_line_grn_po_line",
                columnNames = {"goods_receipt_id", "po_line_id"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceiptLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    @Column(name = "received_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal receivedQty;

    @Column(name = "notes", length = 500)
    private String notes;

    public GoodsReceiptLine(UUID poLineId, BigDecimal receivedQty, String notes) {
        if (poLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng đơn hàng (po_line_id) không được để trống");
        }
        if (receivedQty == null || receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng nhập kho (received_qty) phải lớn hơn 0");
        }

        this.poLineId = poLineId;
        this.receivedQty = receivedQty.setScale(4, RoundingMode.HALF_UP);
        this.notes = (notes == null || notes.isBlank()) ? null : notes.trim();
    }

    public void assignTo(GoodsReceipt goodsReceipt) {
        this.goodsReceipt = goodsReceipt;
    }
}