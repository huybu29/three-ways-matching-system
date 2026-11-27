package com.tmw.backend.matching.candidate;

import com.tmw.backend.matching.scoring.ConfidenceBreakdown;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kết quả CandidateFinder: với mỗi dòng hóa đơn, danh sách dòng PO ứng viên đã xếp hạng (rank 1 = tốt nhất).
 *
 * <p>CandidateFinder KHÔNG phân bổ: cùng một dòng PO có thể xuất hiện ở nhiều dòng hóa đơn
 * (nhiều-một). Việc chia số lượng giữa các dòng là của bộ phân bổ ở giai đoạn 3
 * (xem docs/be5/thiet-ke-phan-bo-mot-nhieu-nhieu-mot.md).</p>
 *
 * @param scannedPoLines số dòng PO đọc từ DB (để theo dõi hiệu năng)
 */
public record CandidateSearchResult(
        UUID invoiceId,
        List<LineCandidates> lines,
        int scannedPoLines
) {
    public CandidateSearchResult {
        lines = List.copyOf(lines);
    }

    public static CandidateSearchResult empty(UUID invoiceId) {
        return new CandidateSearchResult(invoiceId, List.of(), 0);
    }

    public Optional<LineCandidates> forInvoiceLine(UUID invoiceLineId) {
        return lines.stream().filter(l -> l.invoiceLineId().equals(invoiceLineId)).findFirst();
    }

    /** Dòng hóa đơn không có ứng viên nào → engine sinh MISSING_PO ở giai đoạn 3. */
    public List<LineCandidates> linesWithoutCandidates() {
        return lines.stream().filter(l -> l.candidates().isEmpty()).toList();
    }

    public record LineCandidates(
            UUID invoiceLineId,
            int lineNo,
            List<RankedCandidate> candidates
    ) {
        public LineCandidates {
            candidates = List.copyOf(candidates);
        }

        public Optional<RankedCandidate> best() {
            return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
        }
    }

    /**
     * @param poRemainingQty  vế PO: ordered − allocated − reserved
     * @param grnRemainingQty vế GRN: received − allocated − reserved (không âm)
     * @param availableQty    số lượng có thể ghép: min(vế PO, vế GRN) nếu require_grn, ngược lại vế PO
     * @param missingGrn      require_grn nhưng dòng PO chưa có số lượng nhận kho khả dụng → MISSING_GRN ở GĐ3
     */
    public record RankedCandidate(
            int rank,
            UUID purchaseOrderId,
            String poNumber,
            LocalDate orderDate,
            UUID poLineId,
            int poLineNo,
            String itemCode,
            BigDecimal poUnitPrice,
            BigDecimal poRemainingQty,
            BigDecimal grnRemainingQty,
            BigDecimal availableQty,
            boolean missingGrn,
            ConfidenceBreakdown confidence
    ) {
        public BigDecimal score() {
            return confidence.overallScore();
        }
    }
}
