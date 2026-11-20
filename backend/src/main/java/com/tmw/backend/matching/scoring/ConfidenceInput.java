package com.tmw.backend.matching.scoring;

import java.math.BigDecimal;
import java.util.UUID;

public record ConfidenceInput(
        UUID invoicePoIdReference,
        UUID invoiceLinePoLineIdHint,
        UUID candidatePoId,
        UUID candidatePoLineId,
        UUID invoiceSupplierId,
        UUID poSupplierId,
        String invoiceItemCode,
        String poItemCode,
        BigDecimal priceVariancePct,
        BigDecimal priceTolerancePct,
        BigDecimal qtyVariancePct,
        BigDecimal qtyTolerancePct
) {
}