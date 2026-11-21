package com.tmw.backend.posting.mapper;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.dto.MatchProposalResponse;
import com.tmw.backend.posting.api.PostingResult;
import com.tmw.backend.posting.dto.JournalEntryResponse;
import com.tmw.backend.posting.dto.PostingResultResponse;
import com.tmw.backend.posting.entity.JournalEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Mapper converting between {@link JournalEntry} persistence entity,
 * internal {@link PostingResult} domain record, and API response DTOs.
 */
@Component
public class PostingMapper {

    /**
     * Converts a persisted {@link JournalEntry} along with synchronized lifecycle statuses
     * into the internal {@link PostingResult} record.
     */
    public PostingResult toPostingResult(
            JournalEntry entry,
            MatchStatus matchStatus,
            InvoiceStatus invoiceStatus
    ) {
        if (entry == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JournalEntry không được null khi ánh xạ PostingResult");
        }
        if (matchStatus == null || invoiceStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái Match và Invoice không được null khi ánh xạ PostingResult");
        }

        Instant postedAtInstant = entry.getPostedAt() != null ? entry.getPostedAt().toInstant() : null;
        BigDecimal normalizedAmount = entry.getTotalAmount() != null
                ? entry.getTotalAmount().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        return new PostingResult(
                entry.getId(),
                entry.getEntryNumber(),
                entry.getMatchId(),
                entry.getInvoiceId(),
                matchStatus,
                invoiceStatus,
                entry.getPostingDate(),
                entry.getCurrency(),
                normalizedAmount,
                entry.getPaymentInstructionId(),
                entry.getPostedBy(),
                postedAtInstant
        );
    }

    /**
     * Converts a {@link JournalEntry} and {@link MatchProposal} into the OpenAPI {@link PostingResultResponse}.
     */
    public PostingResultResponse toPostingResultResponse(JournalEntry entry, MatchProposal proposal) {
        if (entry == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JournalEntry không được null khi ánh xạ PostingResultResponse");
        }
        MatchProposalResponse matchResponse = proposal != null ? MatchProposalResponse.from(proposal) : null;
        return toPostingResultResponse(entry, matchResponse);
    }

    /**
     * Converts a {@link JournalEntry} and pre-mapped {@link MatchProposalResponse} into {@link PostingResultResponse}.
     */
    public PostingResultResponse toPostingResultResponse(JournalEntry entry, MatchProposalResponse matchResponse) {
        if (entry == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JournalEntry không được null khi ánh xạ PostingResultResponse");
        }
        return PostingResultResponse.builder()
                .match(matchResponse)
                .journalEntryId(entry.getEntryNumber())
                .postedAt(entry.getPostedAt())
                .paymentInstructionId(Objects.toString(entry.getPaymentInstructionId(), null))
                .build();
    }

    /**
     * Converts an internal {@link PostingResult} record and {@link MatchProposal} into {@link PostingResultResponse}.
     */
    public PostingResultResponse toPostingResultResponse(PostingResult result, MatchProposal proposal) {
        if (result == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PostingResult không được null khi ánh xạ PostingResultResponse");
        }
        MatchProposalResponse matchResponse = proposal != null ? MatchProposalResponse.from(proposal) : null;
        return PostingResultResponse.builder()
                .match(matchResponse)
                .journalEntryId(result.entryNumber())
                .postedAt(result.postedAt() != null ? result.postedAt().atOffset(ZoneOffset.UTC) : null)
                .paymentInstructionId(Objects.toString(result.paymentInstructionId(), null))
                .build();
    }

    /**
     * Converts a {@link JournalEntry} entity into its detailed {@link JournalEntryResponse} DTO.
     */
    public JournalEntryResponse toJournalEntryResponse(JournalEntry entry) {
        if (entry == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JournalEntry không được null khi ánh xạ JournalEntryResponse");
        }
        return JournalEntryResponse.fromEntity(entry);
    }
}
