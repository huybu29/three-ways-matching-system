package com.tmw.backend.allocation.api;

import java.math.BigDecimal;
import java.util.UUID;

public record AllocationLineRequest(
        UUID poLineId,
        BigDecimal qty,
        BigDecimal amount
) {
}