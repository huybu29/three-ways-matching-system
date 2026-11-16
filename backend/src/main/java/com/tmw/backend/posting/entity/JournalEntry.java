package com.tmw.backend.posting.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity representing an accounting posting result (Bút toán hạch toán kế toán).
 * Designed to be immutable after creation according to financial audit standards (GAAP/VAS).
 * Stores journal entry number, match reference, financial amounts, and payment instruction.
 */
@Entity
@Table(name = "journal_entries")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Unique business identifier for the journal entry (e.g. JE-202611-0001 or UUID string).
     * Exposed as journal_entry_id in API PostingResult response.
     */
    @Column(name = "journal_entry_id", nullable = false, unique = true, length = 64, updatable = false)
    private String journalEntryId;

    /**
     * Associated MatchProposal identifier.
     * Each Match has at most one JournalEntry (one-to-one relationship).
     */
    @Column(name = "match_id", nullable = false, unique = true, updatable = false)
    private UUID matchId;

    /**
     * Total posting amount including VAT.
     * Financial precision 19, scale 4 with HALF_UP rounding.
     */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalAmount;

    /**
     * Total input VAT amount.
     * Financial precision 19, scale 4. Invariant: 0 <= vatAmount <= totalAmount.
     */
    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal vatAmount;

    /**
     * Payment instruction reference id for ERP or banking integration (optional).
     */
    @Column(name = "payment_instruction_id", length = 100, updatable = false)
    private String paymentInstructionId;

    /**
     * Authenticated User ID (Poster) who committed this posting.
     */
    @Column(name = "posted_by", nullable = false, updatable = false)
    private UUID postedBy;

    /**
     * Timestamp when the posting was committed.
     */
    @Column(name = "posted_at", nullable = false, updatable = false)
    private OffsetDateTime postedAt;

    /**
     * System creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (this.postedAt == null) {
            this.postedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (this.journalEntryId == null || this.journalEntryId.isBlank()) {
            this.journalEntryId = "JE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        validateInvariants();
    }

    /**
     * Validates domain invariants for accounting posting integrity.
     */
    public void validateInvariants() {
        Objects.requireNonNull(matchId, "matchId cannot be null");
        Objects.requireNonNull(totalAmount, "totalAmount cannot be null");
        Objects.requireNonNull(vatAmount, "vatAmount cannot be null");
        Objects.requireNonNull(postedBy, "postedBy cannot be null");

        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalAmount cannot be negative");
        }
        if (vatAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("vatAmount cannot be negative");
        }
        if (vatAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("vatAmount cannot exceed totalAmount");
        }
    }
}

