package com.tmw.backend.document.entity;

import com.tmw.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceipt extends BaseEntity {

    @Column(name = "grn_number", nullable = false, unique = true)
    private String grnNumber;

    @Column(name = "po_id", nullable = false)
    private java.util.UUID poId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;
}