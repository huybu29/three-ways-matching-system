package com.tmw.backend.allocation.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CapacitySnapshot(
        UUID poLineId,
        BigDecimal orderedQty,
        BigDecimal allocatedQty,
        BigDecimal reservedQty,
        BigDecimal receivedQty,
        BigDecimal remainingQty,
        BigDecimal orderedAmount,
        BigDecimal allocatedAmount,
        BigDecimal reservedAmount,
        BigDecimal receivedAmount,
        BigDecimal remainingAmount
) {
}