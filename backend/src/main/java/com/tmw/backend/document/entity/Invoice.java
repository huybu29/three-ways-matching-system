package com.tmw.backend.document.entity;

import com.tmw.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @Column(name = "invoice_series", nullable = false)
    private String invoiceSeries;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "subtotal", precision = 18, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", precision = 18, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "total", precision = 18, scale = 4)
    private BigDecimal total;
}