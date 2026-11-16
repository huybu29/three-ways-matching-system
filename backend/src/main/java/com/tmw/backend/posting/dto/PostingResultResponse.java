package com.tmw.backend.posting.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO matching the OpenAPI PostingResult schema.
 * Returned upon successful completion of POST /matches/{matchId}/post.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingResultResponse {

    @JsonProperty("match")
    private Object match;

    @JsonProperty("journal_entry_id")
    private String journalEntryId;

    @JsonProperty("posted_at")
    private OffsetDateTime postedAt;

    @JsonProperty("payment_instruction_id")
    private String paymentInstructionId;
}

