package com.tmw.backend.invoice.api;

import java.util.UUID;

public interface InvoiceLifecycleApi {

    InvoiceLifecycleSnapshot markMatching(UUID invoiceId);

    InvoiceLifecycleSnapshot markMatched(UUID invoiceId);

    InvoiceLifecycleSnapshot markException(UUID invoiceId, String reason);

    InvoiceLifecycleSnapshot markApproved(UUID invoiceId, UUID approverId, String reason);

    InvoiceLifecycleSnapshot markPosted(UUID invoiceId, UUID posterId);

    InvoiceLifecycleSnapshot markRejected(UUID invoiceId, String reason);

    InvoiceLifecycleSnapshot markVoid(UUID invoiceId, String reason);

    InvoiceLifecycleSnapshot resetToReceived(UUID invoiceId);
}