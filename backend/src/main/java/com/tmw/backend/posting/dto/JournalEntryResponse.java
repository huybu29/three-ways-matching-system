package com.tmw.backend.posting.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.posting.entity.JournalEntry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed DTO representation of an accounting JournalEntry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("journal_entry_id")
    private String journalEntryId;

    @JsonProperty("match_id")
    private UUID matchId;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("vat_amount")
    private BigDecimal vatAmount;

    @JsonProperty("payment_instruction_id")
    private String paymentInstructionId;

    @JsonProperty("posted_by")
    private UUID postedBy;

    @JsonProperty("posted_at")
    private OffsetDateTime postedAt;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    public static JournalEntryResponse fromEntity(JournalEntry entity) {
        if (entity == null) {
            return null;
        }
        return JournalEntryResponse.builder()
                .id(entity.getId())
                .journalEntryId(entity.getJournalEntryId())
                .matchId(entity.getMatchId())
                .totalAmount(entity.getTotalAmount())
                .vatAmount(entity.getVatAmount())
                .paymentInstructionId(entity.getPaymentInstructionId())
                .postedBy(entity.getPostedBy())
                .postedAt(entity.getPostedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

