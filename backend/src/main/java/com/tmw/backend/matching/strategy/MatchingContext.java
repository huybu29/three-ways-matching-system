package com.tmw.backend.matching.strategy;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.variance.ToleranceThreshold;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MatchingContext(
        UUID invoiceId,
        UUID invoiceSupplierId,
        UUID invoicePoIdReference,
        CurrencyCode currency,
        MatchSource source,
        ToleranceThreshold tolerance,
        List<InvoiceLineCandidate> invoiceLines,
        List<PoLineCandidate> poLines
) {
    public record InvoiceLineCandidate(
            UUID invoiceLineId,
            int lineNo,
            UUID poLineIdHint,
            String itemCode,
            BigDecimal unmatchedQty,
            BigDecimal unitPrice
    ) {
    }

    public record PoLineCandidate(
            UUID poId,
            UUID poLineId,
            UUID supplierId,
            String itemCode,
            BigDecimal unitPrice,
            BigDecimal remainingCapacityQty,
            BigDecimal receivedQty,
            UUID latestGrnLineId
    ) {
    }
}