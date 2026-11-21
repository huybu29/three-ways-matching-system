package com.tmw.backend.invoice.api;

import java.util.UUID;

public record InvoiceLifecycleSnapshot(
        UUID invoiceId,
        InvoiceStatus status,
        Long version,
        UUID approvedBy,
        UUID postedBy,
        String statusReason
) {
}