package com.tmw.backend.posting.api;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.matching.api.MatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PostingResult(
        UUID journalEntryId,
        String entryNumber,
        UUID matchId,
        UUID invoiceId,
        MatchStatus matchStatus,
        InvoiceStatus invoiceStatus,
        LocalDate postingDate,
        CurrencyCode currency,
        BigDecimal postedAmount,
        UUID paymentInstructionId,
        UUID postedBy,
        Instant postedAt
) {
}