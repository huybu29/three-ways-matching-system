package com.tmw.backend.matching.scoring;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dữ liệu chấm điểm tin cậy cho một cặp (dòng hóa đơn ↔ dòng PO ứng viên).
 * Mọi trường đều có thể null; thiếu dữ liệu thì tiêu chí tương ứng được điểm thấp, không ném lỗi.
 *
 * @param invoicePoReference      số PO in trên hóa đơn (chuỗi, vd "PO-2026-0012")
 * @param invoicePoIdHint         PO gợi ý ở header hóa đơn (invoice.purchase_order_id)
 * @param invoiceLinePoLineIdHint dòng PO gợi ý ở dòng hóa đơn (invoice_line.po_line_id)
 * @param candidatePoNumber       purchase_order.po_number của ứng viên
 * @param candidatePoId           purchase_order.id của ứng viên
 * @param candidatePoLineId       po_line.id của ứng viên
 * @param priceVariancePct        |giá HĐ − giá PO| / giá PO × 100
 * @param qtyVariancePct          phần trăm số lượng hóa đơn KHÔNG được ứng viên đáp ứng (0 = đủ)
 */
public record ConfidenceInput(
        String invoicePoReference,
        UUID invoicePoIdHint,
        UUID invoiceLinePoLineIdHint,
        String candidatePoNumber,
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String invoicePoReference;
        private UUID invoicePoIdHint;
        private UUID invoiceLinePoLineIdHint;
        private String candidatePoNumber;
        private UUID candidatePoId;
        private UUID candidatePoLineId;
        private UUID invoiceSupplierId;
        private UUID poSupplierId;
        private String invoiceItemCode;
        private String poItemCode;
        private BigDecimal priceVariancePct;
        private BigDecimal priceTolerancePct;
        private BigDecimal qtyVariancePct;
        private BigDecimal qtyTolerancePct;

        private Builder() {
        }

        public Builder invoicePoReference(String v) { this.invoicePoReference = v; return this; }
        public Builder invoicePoIdHint(UUID v) { this.invoicePoIdHint = v; return this; }
        public Builder invoiceLinePoLineIdHint(UUID v) { this.invoiceLinePoLineIdHint = v; return this; }
        public Builder candidatePoNumber(String v) { this.candidatePoNumber = v; return this; }
        public Builder candidatePoId(UUID v) { this.candidatePoId = v; return this; }
        public Builder candidatePoLineId(UUID v) { this.candidatePoLineId = v; return this; }
        public Builder invoiceSupplierId(UUID v) { this.invoiceSupplierId = v; return this; }
        public Builder poSupplierId(UUID v) { this.poSupplierId = v; return this; }
        public Builder invoiceItemCode(String v) { this.invoiceItemCode = v; return this; }
        public Builder poItemCode(String v) { this.poItemCode = v; return this; }
        public Builder priceVariancePct(BigDecimal v) { this.priceVariancePct = v; return this; }
        public Builder priceTolerancePct(BigDecimal v) { this.priceTolerancePct = v; return this; }
        public Builder qtyVariancePct(BigDecimal v) { this.qtyVariancePct = v; return this; }
        public Builder qtyTolerancePct(BigDecimal v) { this.qtyTolerancePct = v; return this; }

        public ConfidenceInput build() {
            return new ConfidenceInput(
                    invoicePoReference, invoicePoIdHint, invoiceLinePoLineIdHint,
                    candidatePoNumber, candidatePoId, candidatePoLineId,
                    invoiceSupplierId, poSupplierId, invoiceItemCode, poItemCode,
                    priceVariancePct, priceTolerancePct, qtyVariancePct, qtyTolerancePct
            );
        }
    }
}
