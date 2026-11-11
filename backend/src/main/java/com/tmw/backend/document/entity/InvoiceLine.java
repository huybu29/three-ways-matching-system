package com.tmw.backend.document.entity;

import com.tmw.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "description")
    private String description;

    @Column(name = "qty", precision = 18, scale = 4, nullable = false)
    private BigDecimal qty;

    @Column(name = "matched_qty", precision = 18, scale = 4, nullable = false)
    private BigDecimal matchedQty;

    @Column(name = "unit_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal vatRate;
}