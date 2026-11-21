package com.tmw.backend.masterdata.service;

import java.util.UUID;

/** Một phát hiện sai lệch thuế. BE-7 (ExceptionFactoryService) dùng để tạo MatchException. */
public record TaxFinding(
        TaxFindingType type,
        UUID invoiceLineId,
        String expected,
        String actual,
        String message
) {
    public enum TaxFindingType {
        TAX_CODE_MISMATCH,
        VAT_RATE_MISMATCH
    }
}
