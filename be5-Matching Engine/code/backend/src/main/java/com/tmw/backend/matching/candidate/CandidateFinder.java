package com.tmw.backend.matching.candidate;

import com.tmw.backend.matching.candidate.CandidateSearchRequest.InvoiceLineToMatch;
import com.tmw.backend.matching.candidate.CandidateSearchResult.LineCandidates;
import com.tmw.backend.matching.candidate.CandidateSearchResult.RankedCandidate;
import com.tmw.backend.matching.scoring.ConfidenceBreakdown;
import com.tmw.backend.matching.scoring.ConfidenceInput;
import com.tmw.backend.matching.scoring.ConfidenceScorer;
import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import com.tmw.backend.purchaseorder.api.PoLineCandidateView;
import com.tmw.backend.purchaseorder.api.PurchaseOrderQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.tmw.backend.common.decimal.DecimalScales.INTERMEDIATE_SCALE;
import static com.tmw.backend.common.decimal.DecimalScales.PCT_SCALE;
import static com.tmw.backend.common.decimal.DecimalScales.QTY_SCALE;
import static com.tmw.backend.common.decimal.DecimalScales.ROUNDING;

/**
 * Tìm và xếp hạng dòng PO ứng viên cho từng dòng hóa đơn (giai đoạn 2, BE-5).
 *
 * <p>Luồng: một truy vấn duy nhất qua PurchaseOrderQueryApi cho cả hóa đơn (nhà cung cấp, PO mở, ngày đặt
 * &lt;= ngày hóa đơn, cùng tiền tệ, còn hạn mức PO, trùng mã hàng hoặc gợi ý PO) → với mỗi dòng hóa đơn,
 * giữ các dòng PO liên quan (mã hàng khớp đủ/một phần, hoặc được chỉ định) → chấm điểm bằng ConfidenceScorer
 * → sắp xếp: điểm giảm dần, rồi PO cũ trước (FIFO), số PO, số dòng.</p>
 *
 * <p>Chỉ đọc, không khóa, không giữ chỗ. Số dư trong kết quả có thể đã cũ khi giữ chỗ thật;
 * CapacityAllocationService mới là nơi quyết định cuối cùng.</p>
 */
@Component
@RequiredArgsConstructor
public class CandidateFinder {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PurchaseOrderQueryApi purchaseOrderQueryApi;
    private final ConfidenceScorer confidenceScorer;
    private final CandidateFinderProperties properties;

    public CandidateSearchResult find(CandidateSearchRequest request) {
        if (request.lines().isEmpty()) {
            return CandidateSearchResult.empty(request.invoiceId());
        }

        List<PoLineCandidateView> poLines = purchaseOrderQueryApi.findCandidateLines(toQuery(request));

        List<LineCandidates> result = new ArrayList<>(request.lines().size());
        for (InvoiceLineToMatch line : request.lines()) {
            result.add(new LineCandidates(line.invoiceLineId(), line.lineNo(), rankForLine(request, line, poLines)));
        }
        return new CandidateSearchResult(request.invoiceId(), result, poLines.size());
    }

    // ------------------------------------------------------------------ query

    private PoLineCandidateQuery toQuery(CandidateSearchRequest request) {
        Set<String> itemCodes = new LinkedHashSet<>();
        Set<UUID> poLineHints = new LinkedHashSet<>();
        for (InvoiceLineToMatch line : request.lines()) {
            if (line.itemCode() != null) {
                itemCodes.add(line.itemCode());
            }
            if (line.poLineIdHint() != null) {
                poLineHints.add(line.poLineIdHint());
            }
        }
        return new PoLineCandidateQuery(
                request.supplierId(),
                request.issueDate(),
                request.currency(),
                itemCodes,
                poLineHints,
                request.poReference(),
                request.purchaseOrderIdHint(),
                properties.getMaxScannedPoLines()
        );
    }

    // ------------------------------------------------------------------ ranking

    private List<RankedCandidate> rankForLine(
            CandidateSearchRequest request,
            InvoiceLineToMatch line,
            List<PoLineCandidateView> poLines
    ) {
        List<Scored> scored = new ArrayList<>();
        for (PoLineCandidateView po : poLines) {
            BigDecimal available = availableQty(po, request.requireGrn());
            ConfidenceBreakdown breakdown = confidenceScorer.score(ConfidenceInput.builder()
                    .invoicePoReference(request.poReference())
                    .invoicePoIdHint(request.purchaseOrderIdHint())
                    .invoiceLinePoLineIdHint(line.poLineIdHint())
                    .candidatePoNumber(po.poNumber())
                    .candidatePoId(po.purchaseOrderId())
                    .candidatePoLineId(po.poLineId())
                    .invoiceSupplierId(request.supplierId())
                    .poSupplierId(po.supplierId())
                    .invoiceItemCode(line.itemCode())
                    .poItemCode(po.itemCode())
                    .priceVariancePct(priceVariancePct(line.unitPrice(), po.unitPrice()))
                    .priceTolerancePct(request.priceTolerancePct())
                    .qtyVariancePct(shortagePct(line.unmatchedQty(), available))
                    .qtyTolerancePct(request.qtyTolerancePct())
                    .build());

            boolean hinted = po.poLineId().equals(line.poLineIdHint());
            if (breakdown.itemCodeScore().signum() == 0 && !hinted) {
                continue; // dòng PO của PO được gợi ý nhưng khác mặt hàng → không liên quan dòng này
            }
            if (breakdown.overallScore().compareTo(properties.getMinScore()) < 0) {
                continue;
            }
            scored.add(new Scored(po, breakdown, available, request.requireGrn()));
        }

        scored.sort(Comparator
                .comparing((Scored s) -> s.breakdown().overallScore()).reversed()
                .thenComparing(s -> s.po().orderDate())
                .thenComparing(s -> s.po().poNumber())
                .thenComparingInt(s -> s.po().lineNo()));

        List<RankedCandidate> ranked = new ArrayList<>();
        int limit = Math.min(scored.size(), properties.getMaxPerLine());
        for (int i = 0; i < limit; i++) {
            ranked.add(scored.get(i).toRanked(i + 1));
        }
        return ranked;
    }

    /** Số lượng có thể ghép theo các vế hạn mức (mục 1.2): vế PO, và vế GRN khi require_grn. */
    static BigDecimal availableQty(PoLineCandidateView po, boolean requireGrn) {
        BigDecimal poRemaining = po.remainingQty();
        BigDecimal available = requireGrn ? poRemaining.min(po.grnRemainingQty()) : poRemaining;
        return (available.signum() < 0 ? BigDecimal.ZERO : available).setScale(QTY_SCALE, ROUNDING);
    }

    /** Phần trăm số lượng hóa đơn mà ứng viên KHÔNG đáp ứng được: 0% = đủ, 100% = không còn gì. */
    static BigDecimal shortagePct(BigDecimal invoiceQty, BigDecimal availableQty) {
        BigDecimal covered = invoiceQty.min(availableQty.max(BigDecimal.ZERO));
        return invoiceQty.subtract(covered)
                .multiply(HUNDRED)
                .divide(invoiceQty, INTERMEDIATE_SCALE, ROUNDING)
                .setScale(PCT_SCALE, ROUNDING);
    }

    /** Cùng quy ước với VarianceCalculator: |giá HĐ − giá PO| / giá PO × 100; giá PO = 0 thì 0% hoặc 100%. */
    static BigDecimal priceVariancePct(BigDecimal invoicePrice, BigDecimal poPrice) {
        if (poPrice == null || invoicePrice == null) {
            return null;
        }
        if (poPrice.signum() == 0) {
            return (invoicePrice.signum() == 0 ? BigDecimal.ZERO : HUNDRED).setScale(PCT_SCALE, ROUNDING);
        }
        return invoicePrice.subtract(poPrice).abs()
                .multiply(HUNDRED)
                .divide(poPrice, INTERMEDIATE_SCALE, ROUNDING)
                .setScale(PCT_SCALE, ROUNDING);
    }

    private record Scored(PoLineCandidateView po, ConfidenceBreakdown breakdown, BigDecimal available, boolean requireGrn) {
        RankedCandidate toRanked(int rank) {
            BigDecimal grnRemaining = po.grnRemainingQty().setScale(QTY_SCALE, ROUNDING);
            return new RankedCandidate(
                    rank,
                    po.purchaseOrderId(),
                    po.poNumber(),
                    po.orderDate(),
                    po.poLineId(),
                    po.lineNo(),
                    po.itemCode(),
                    po.unitPrice(),
                    po.remainingQty().setScale(QTY_SCALE, ROUNDING),
                    grnRemaining,
                    available,
                    requireGrn && grnRemaining.signum() == 0,
                    breakdown
            );
        }
    }
}
