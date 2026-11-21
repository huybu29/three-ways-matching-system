package com.tmw.backend.posting.service;

import com.tmw.backend.common.enums.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Financial context passed into {@link PostingService} when committing a journal entry.
 * Keeps the posting module decoupled from invoice internal entities/repositories.
 */
public record PostingCommand(
        UUID supplierId,
        LocalDate postingDate,
        CurrencyCode currency,
        BigDecimal totalAmount,
        BigDecimal vatAmount,
        String description
) {
}
