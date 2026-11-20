package com.tmw.backend.matchexception.api;

public enum ExceptionType {
    QTY_VARIANCE,
    PRICE_VARIANCE,
    AMOUNT_VARIANCE,
    MISSING_PO,
    MISSING_GRN,
    DUPLICATE_INVOICE,
    TAX_CODE_MISMATCH,
    CURRENCY_MISMATCH
}