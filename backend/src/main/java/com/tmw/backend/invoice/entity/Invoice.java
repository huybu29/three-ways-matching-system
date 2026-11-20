package com.tmw.backend.invoice.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "invoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends BaseEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "invoice_series", nullable = false, length = 50)
    private String invoiceSeries;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private CurrencyCode currency = CurrencyCode.VND;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status = InvoiceStatus.RECEIVED;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Column(name = "created_by")
    private UUID createdBy;

    @Setter
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Setter
    @Column(name = "posted_by")
    private UUID postedBy;

    @Setter
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice(
            UUID supplierId,
            UUID purchaseOrderId,
            String invoiceSeries,
            String invoiceNumber,
            LocalDate issueDate,
            CurrencyCode currency,
            UUID createdBy
    ) {
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhà cung cấp (supplier_id) không được để trống");
        }
        if (invoiceSeries == null || invoiceSeries.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ký hiệu hóa đơn (invoice_series) không được để trống");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số hóa đơn (invoice_number) không được để trống");
        }
        if (issueDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày hóa đơn (issue_date) không được để trống");
        }

        this.supplierId = supplierId;
        this.purchaseOrderId = purchaseOrderId;
        this.invoiceSeries = invoiceSeries.trim().toUpperCase(Locale.ROOT);
        this.invoiceNumber = invoiceNumber.trim();
        this.issueDate = issueDate;
        this.currency = currency != null ? currency : CurrencyCode.VND;
        this.status = InvoiceStatus.RECEIVED;
        this.createdBy = createdBy;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(InvoiceLine line) {
        if (line == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng hóa đơn không được null");
        }
        boolean duplicateLineNo = lines.stream()
                .anyMatch(existing -> existing.getLineNo() != null && existing.getLineNo().equals(line.getLineNo()));
        if (duplicateLineNo) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trùng số thứ tự dòng (line_no = " + line.getLineNo() + ") trong hóa đơn " + invoiceNumber
            );
        }

        lines.add(line);
        line.assignTo(this);
        recalculateTotals();
    }

    public void removeLine(InvoiceLine line) {
        if (line != null && lines.remove(line)) {
            line.assignTo(null);
            recalculateTotals();
        }
    }

    public void recalculateTotals() {
        BigDecimal sumSub = BigDecimal.ZERO;
        BigDecimal sumVat = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            sumSub = sumSub.add(line.getLineAmount());
            sumVat = sumVat.add(line.getVatAmount());
        }
        this.subtotal = sumSub.setScale(4, RoundingMode.HALF_UP);
        this.vatAmount = sumVat.setScale(4, RoundingMode.HALF_UP);
        this.total = this.subtotal.add(this.vatAmount).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Chỉ được gọi thông qua InvoiceStateMachine để đảm bảo tuân thủ ma trận chuyển trạng thái.
     */
    public void applyTransition(InvoiceStatus nextStatus, String reason) {
        if (nextStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Trạng thái đích không được null");
        }
        this.status = nextStatus;
        if (reason != null && !reason.isBlank()) {
            this.statusReason = reason.trim();
        }
    }
}