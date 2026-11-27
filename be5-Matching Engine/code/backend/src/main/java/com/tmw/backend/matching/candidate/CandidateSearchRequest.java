package com.tmw.backend.matching.candidate;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.variance.ToleranceThreshold;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu tìm ứng viên cho một hóa đơn. Ở giai đoạn 3, match-runs dựng đối tượng này từ Invoice/InvoiceLine.
 *
 * @param poReference         số PO in trên hóa đơn (chuỗi); null nếu không có (chờ BE-4 thêm invoice.po_reference)
 * @param purchaseOrderIdHint invoice.purchase_order_id nếu người nhập đã chọn PO
 * @param tolerance           ngưỡng dung sai (từ ToleranceEvaluationService của BE-2); null = chưa có chính sách,
 *                            khi đó coi như require_grn = true và dung sai 0%
 */
public record CandidateSearchRequest(
        UUID invoiceId,
        UUID supplierId,
        LocalDate issueDate,
        String currency,
        String poReference,
        UUID purchaseOrderIdHint,
        ToleranceThreshold tolerance,
        List<InvoiceLineToMatch> lines
) {
    public CandidateSearchRequest {
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "supplierId của hóa đơn không được để trống");
        }
        if (issueDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "issueDate của hóa đơn không được để trống");
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** Thiếu chính sách thì mặc định yêu cầu GRN (an toàn hơn cho đối chiếu 3 chiều). */
    public boolean requireGrn() {
        return tolerance == null || tolerance.requireGrn();
    }

    public BigDecimal qtyTolerancePct() {
        return tolerance == null ? null : tolerance.qtyTolerancePct();
    }

    public BigDecimal priceTolerancePct() {
        return tolerance == null ? null : tolerance.priceTolerancePct();
    }

    /**
     * Một dòng hóa đơn cần ghép.
     *
     * @param unmatchedQty số lượng còn chưa ghép (invoice_line.qty − matched_qty), phải &gt; 0
     * @param poLineIdHint invoice_line.po_line_id nếu có
     */
    public record InvoiceLineToMatch(
            UUID invoiceLineId,
            int lineNo,
            String itemCode,
            BigDecimal unmatchedQty,
            BigDecimal unitPrice,
            UUID poLineIdHint
    ) {
        public InvoiceLineToMatch {
            if (invoiceLineId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceLineId không được để trống");
            }
            if (unmatchedQty == null || unmatchedQty.signum() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Số lượng chưa ghép của dòng hóa đơn " + lineNo + " phải lớn hơn 0");
            }
            if (unitPrice == null || unitPrice.signum() < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Đơn giá dòng hóa đơn " + lineNo + " không được null hoặc âm");
            }
        }
    }
}
