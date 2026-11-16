package com.tmw.backend.document.entity;

import com.tmw.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceiptLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "po_line_id", nullable = false)
    private java.util.UUID poLineId;

    @Column(name = "received_qty", precision = 18, scale = 4, nullable = false)
    private BigDecimal receivedQty;
}