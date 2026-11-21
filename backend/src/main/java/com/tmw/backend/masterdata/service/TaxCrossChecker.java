package com.tmw.backend.masterdata.service;

import com.tmw.backend.masterdata.service.TaxFinding.TaxFindingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đối chiếu thuế: MST nhà cung cấp (master data) so với MST trên hóa đơn,
 * và thuế suất VAT dòng hóa đơn so với dòng PO. Thuần logic, không truy cập DB.
 */
@Component
public class TaxCrossChecker {

    public record VatLine(UUID invoiceLineId, BigDecimal invoiceVatRate, BigDecimal poVatRate) {
    }

    public List<TaxFinding> check(String supplierTaxCode, String invoiceTaxCode, List<VatLine> lines) {
        List<TaxFinding> findings = new ArrayList<>();

        String expectedTax = normalizeTaxCode(supplierTaxCode);
        String actualTax = normalizeTaxCode(invoiceTaxCode);
        if (expectedTax == null || !expectedTax.equals(actualTax)) {
            findings.add(new TaxFinding(TaxFindingType.TAX_CODE_MISMATCH, null, expectedTax, actualTax,
                    "MST trên hóa đơn không khớp MST nhà cung cấp"));
        }

        if (lines != null) {
            for (VatLine line : lines) {
                if (line.invoiceVatRate() == null || line.poVatRate() == null
                        || line.invoiceVatRate().compareTo(line.poVatRate()) != 0) {
                    findings.add(new TaxFinding(TaxFindingType.VAT_RATE_MISMATCH, line.invoiceLineId(),
                            plain(line.poVatRate()), plain(line.invoiceVatRate()),
                            "Thuế suất VAT trên hóa đơn không khớp PO"));
                }
            }
        }
        return findings;
    }

    private static String normalizeTaxCode(String value) {
        if (value == null) return null;
        String v = value.replaceAll("\\s+", "");
        return v.isEmpty() ? null : v;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
