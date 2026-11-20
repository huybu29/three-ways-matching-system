package com.tmw.backend.posting.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "journal_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_journal_entry_match_id", columnNames = "match_id"),
        @UniqueConstraint(name = "uk_journal_entry_entry_number", columnNames = "entry_number")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry extends BaseEntity {

    @Column(name = "entry_number", nullable = false, length = 50)
    private String entryNumber;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "payment_instruction_id")
    private UUID paymentInstructionId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private CurrencyCode currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "posted_by", nullable = false)
    private UUID postedBy;

    @Column(name = "posted_at", nullable = false)
    private java.time.OffsetDateTime postedAt;

    @Column(name = "description", length = 500)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public JournalEntry(
            String entryNumber,
            UUID matchId,
            UUID invoiceId,
            UUID supplierId,
            LocalDate postingDate,
            CurrencyCode currency,
            BigDecimal totalAmount,
            BigDecimal vatAmount,
            UUID postedBy,
            String description
    ) {
        if (entryNumber == null || entryNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số chứng từ ghi sổ (entry_number) không được để trống");
        }
        if (matchId == null || invoiceId == null || supplierId == null || postingDate == null || currency == null || postedBy == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin bắt buộc khi tạo JournalEntry");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tổng tiền hạch toán phải lớn hơn 0");
        }

        this.entryNumber = entryNumber.trim().toUpperCase(Locale.ROOT);
        this.matchId = matchId;
        this.invoiceId = invoiceId;
        this.supplierId = supplierId;
        this.paymentInstructionId = null;
        this.postingDate = postingDate;
        this.currency = currency;
        this.totalAmount = totalAmount.setScale(4, RoundingMode.HALF_UP);
        this.vatAmount = (vatAmount != null ? vatAmount : BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        this.postedBy = postedBy;
        this.postedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        this.description = (description == null || description.isBlank()) ? null : description.trim();
    }

    public String getJournalEntryId() {
    return this.entryNumber;
    }

    public void attachPaymentInstruction(UUID paymentInstructionId) {
        if (paymentInstructionId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "paymentInstructionId không được null");
        }
        if (this.paymentInstructionId != null && !this.paymentInstructionId.equals(paymentInstructionId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Bút toán " + entryNumber + " đã được gắn với lệnh thanh toán khác"
            );
        }
        this.paymentInstructionId = paymentInstructionId;
    }
}