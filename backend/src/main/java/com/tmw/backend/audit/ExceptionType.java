package com.tmw.backend.audit;

public enum ExceptionType {
    PRICE_VARIANCE,
    QTY_VARIANCE,
    MISSING_GRN,
    DUPLICATE_INVOICE,
    VAT_RATE_MISMATCH,
    TAX_CODE_MISMATCH,
    CAPACITY_EXCEEDED,
    NO_CANDIDATE_PO
}