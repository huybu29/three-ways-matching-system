package com.tmw.backend.invoice.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum InvoiceStatus {
    RECEIVED,
    MATCHING,
    MATCHED,
    EXCEPTION,
    APPROVED,
    POSTED,
    REJECTED,
    VOID;

    public Set<InvoiceStatus> allowedNextStates() {
        EnumSet<InvoiceStatus> nextStates = switch (this) {
            case RECEIVED -> EnumSet.of(MATCHING, REJECTED, VOID);
            case MATCHING -> EnumSet.of(MATCHED, EXCEPTION, RECEIVED, REJECTED, VOID);
            case MATCHED -> EnumSet.of(APPROVED, MATCHING, RECEIVED, REJECTED, VOID);
            case EXCEPTION -> EnumSet.of(APPROVED, MATCHING, RECEIVED, REJECTED, VOID);
            case APPROVED -> EnumSet.of(POSTED, RECEIVED, REJECTED, VOID);
            case POSTED, REJECTED, VOID -> EnumSet.noneOf(InvoiceStatus.class);
        };
        return Collections.unmodifiableSet(nextStates);
    }

    public boolean canTransitionTo(InvoiceStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    public boolean isTerminal() {
        return allowedNextStates().isEmpty();
    }

    public boolean requiresReason() {
        return this == REJECTED || this == VOID;
    }
}