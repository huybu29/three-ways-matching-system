package com.tmw.backend.posting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.matching.dto.MatchProposalResponse;
import com.tmw.backend.posting.entity.JournalEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingResultResponse {

    @JsonProperty("match")
    private MatchProposalResponse match;

    @JsonProperty("journal_entry_id")
    private String journalEntryId;

    @JsonProperty("posted_at")
    private OffsetDateTime postedAt;

    @JsonProperty("payment_instruction_id")
    private String paymentInstructionId;

    public static PostingResultResponse of(JournalEntry entry, MatchProposalResponse match) {
        if (entry == null) {
            return null;
        }
        return PostingResultResponse.builder()
                .match(match)
                .journalEntryId(entry.getJournalEntryId())
                .postedAt(entry.getPostedAt())
                .paymentInstructionId(Objects.toString(entry.getPaymentInstructionId(), null))
                .build();
    }
}