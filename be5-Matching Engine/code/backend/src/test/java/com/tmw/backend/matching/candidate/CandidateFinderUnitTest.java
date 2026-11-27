package com.tmw.backend.matching.candidate;

import com.tmw.backend.matching.candidate.CandidateSearchRequest.InvoiceLineToMatch;
import com.tmw.backend.matching.candidate.CandidateSearchResult.LineCandidates;
import com.tmw.backend.matching.candidate.CandidateSearchResult.RankedCandidate;
import com.tmw.backend.matching.scoring.ConfidenceScorer;
import com.tmw.backend.matching.scoring.ConfidenceWeightProperties;
import com.tmw.backend.matching.variance.ToleranceThreshold;
import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import com.tmw.backend.purchaseorder.api.PoLineCandidateView;
import com.tmw.backend.purchaseorder.api.PurchaseOrderQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFinderUnitTest {

    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 11, 20);
    private static final ToleranceThreshold THREE_WAY = new ToleranceThreshold(
            new BigDecimal("2.0000"), new BigDecimal("1.0000"), null, true);
    private static final ToleranceThreshold TWO_WAY = new ToleranceThreshold(
            new BigDecimal("2.0000"), new BigDecimal("1.0000"), null, false);

    private FakePurchaseOrderQueryApi poQuery;
    private CandidateFinderProperties properties;
    private CandidateFinder finder;

    @BeforeEach
    void setUp() {
        ConfidenceWeightProperties weights = new ConfidenceWeightProperties();
        weights.validateWeights();
        properties = new CandidateFinderProperties();
        properties.validate();
        poQuery = new FakePurchaseOrderQueryApi();
        finder = new CandidateFinder(poQuery, new ConfidenceScorer(weights), properties);
    }

    // ------------------------------------------------------------------ xếp hạng

    @Test
    void shouldRankPoReferenceMatchFirstThenOlderPoFirstOnEqualScore() {
        PoLineCandidateView olderPo = poLine("PO-2026-00001", LocalDate.of(2026, 1, 5), 1, "ITEM-A", "100", "100", "50");
        PoLineCandidateView newerPo = poLine("PO-2026-00002", LocalDate.of(2026, 3, 5), 1, "ITEM-A", "100", "100", "50");
        PoLineCandidateView referencedPo = poLine("PO-2026-00003", LocalDate.of(2026, 6, 5), 1, "ITEM-A", "100", "100", "50");
        poQuery.rows.addAll(List.of(newerPo, referencedPo, olderPo));

        LineCandidates line = finder.find(request("PO-2026-00003", null, THREE_WAY,
                invoiceLine("ITEM-A", "10", "100", null))).lines().get(0);

        assertThat(line.candidates()).hasSize(3);
        assertThat(line.candidates().get(0).poNumber()).isEqualTo("PO-2026-00003"); // khớp số PO → điểm cao nhất
        assertThat(line.candidates().get(1).poNumber()).isEqualTo("PO-2026-00001"); // cùng điểm → PO cũ trước (FIFO)
        assertThat(line.candidates().get(2).poNumber()).isEqualTo("PO-2026-00002");
        assertThat(line.candidates().get(0).rank()).isEqualTo(1);
        assertThat(line.candidates().get(2).rank()).isEqualTo(3);
        assertNonIncreasingScores(line);
    }

    @Test
    void shouldPreferCandidateWithEnoughQtyAndCloserPrice() {
        PoLineCandidateView shortQty = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 1, "ITEM-A", "100", "3", "3");
        PoLineCandidateView priceOff = poLine("PO-2026-00002", LocalDate.of(2026, 1, 2), 1, "ITEM-A", "120", "100", "100");
        PoLineCandidateView perfect = poLine("PO-2026-00003", LocalDate.of(2026, 1, 3), 1, "ITEM-A", "100", "100", "100");
        poQuery.rows.addAll(List.of(shortQty, priceOff, perfect));

        LineCandidates line = finder.find(request(null, null, THREE_WAY,
                invoiceLine("ITEM-A", "10", "100", null))).lines().get(0);

        assertThat(line.best().orElseThrow().poNumber()).isEqualTo("PO-2026-00003");
        assertNonIncreasingScores(line);
    }

    // ------------------------------------------------------------------ vế GRN

    @Test
    void shouldFlagMissingGrnAndUsePoRemainingWhenGrnNotRequired() {
        PoLineCandidateView notReceived = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 1, "ITEM-A", "100", "100", "0");
        poQuery.rows.add(notReceived);

        RankedCandidate threeWay = finder.find(request(null, null, THREE_WAY,
                invoiceLine("ITEM-A", "10", "100", null))).lines().get(0).best().orElseThrow();
        assertThat(threeWay.missingGrn()).isTrue();
        assertThat(threeWay.availableQty()).isEqualByComparingTo("0");
        assertThat(threeWay.confidence().quantityScore()).isEqualByComparingTo("0.0000");

        RankedCandidate twoWay = finder.find(request(null, null, TWO_WAY,
                invoiceLine("ITEM-A", "10", "100", null))).lines().get(0).best().orElseThrow();
        assertThat(twoWay.missingGrn()).isFalse();
        assertThat(twoWay.availableQty()).isEqualByComparingTo("100");
        assertThat(twoWay.confidence().quantityScore()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldComputeAvailableQtyAsMinOfPoAndGrnLegs() {
        // ordered 100, allocated 30, reserved 10 → vế PO 60; received 50 → vế GRN 10
        PoLineCandidateView view = new PoLineCandidateView(UUID.randomUUID(), "PO-1", SUPPLIER, ISSUE_DATE, "OPEN", "VND",
                UUID.randomUUID(), 1, "ITEM-A", bd("100"), bd("0.1"), bd("100"), bd("30"), bd("10"), bd("50"));

        assertThat(view.remainingQty()).isEqualByComparingTo("60");
        assertThat(view.grnRemainingQty()).isEqualByComparingTo("10");
        assertThat(CandidateFinder.availableQty(view, true)).isEqualByComparingTo("10");
        assertThat(CandidateFinder.availableQty(view, false)).isEqualByComparingTo("60");
    }

    // ------------------------------------------------------------------ chọn dòng liên quan

    @Test
    void shouldIgnoreOtherItemsOfReferencedPoButKeepPartialCodesAndHintedLines() {
        PoLineCandidateView exact = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 1, "ITEM-A", "100", "100", "100");
        PoLineCandidateView otherItem = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 2, "ITEM-Z", "100", "100", "100");
        PoLineCandidateView partialCode = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 3, "ITEM-A-BOX", "100", "100", "100");
        PoLineCandidateView hintedOtherCode = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 4, "SKU-999", "100", "100", "100");
        poQuery.rows.addAll(List.of(exact, otherItem, partialCode, hintedOtherCode));

        LineCandidates line = finder.find(request("PO-2026-00001", null, THREE_WAY,
                invoiceLine("ITEM-A", "10", "100", hintedOtherCode.poLineId()))).lines().get(0);

        assertThat(line.candidates()).extracting(RankedCandidate::poLineNo).containsExactlyInAnyOrder(1, 3, 4);
        assertThat(line.candidates()).noneMatch(c -> c.itemCode().equals("ITEM-Z"));
    }

    @Test
    void shouldLetSamePoLineServeSeveralInvoiceLinesWithoutAllocating() {
        PoLineCandidateView shared = poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 1, "ITEM-A", "100", "100", "100");
        poQuery.rows.add(shared);

        CandidateSearchResult result = finder.find(request(null, null, THREE_WAY,
                invoiceLine("ITEM-A", "60", "100", null),
                invoiceLine("ITEM-A", "60", "100", null)));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines()).allMatch(l -> l.best().orElseThrow().poLineId().equals(shared.poLineId()));
        // Không trừ số dư giữa các dòng: phân bổ là việc của giai đoạn 3
        assertThat(result.lines()).allMatch(l -> l.best().orElseThrow().availableQty().compareTo(bd("100")) == 0);
    }

    @Test
    void shouldReportLinesWithoutCandidates() {
        poQuery.rows.add(poLine("PO-2026-00001", LocalDate.of(2026, 1, 1), 1, "ITEM-A", "100", "100", "100"));

        CandidateSearchResult result = finder.find(request(null, null, THREE_WAY,
                invoiceLine("ITEM-A", "1", "100", null),
                invoiceLine("ITEM-UNKNOWN", "1", "100", null)));

        assertThat(result.linesWithoutCandidates()).hasSize(1);
        assertThat(result.linesWithoutCandidates().get(0).lineNo()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ cấu hình

    @Test
    void shouldApplyMaxPerLineAndMinScore() {
        for (int i = 1; i <= 8; i++) {
            poQuery.rows.add(poLine("PO-2026-0000" + i, LocalDate.of(2026, 1, i), 1, "ITEM-A", "100", "100", "100"));
        }
        properties.setMaxPerLine(3);
        assertThat(finder.find(request(null, null, THREE_WAY, invoiceLine("ITEM-A", "1", "100", null)))
                .lines().get(0).candidates()).hasSize(3);

        properties.setMinScore(new BigDecimal("0.9999"));
        assertThat(finder.find(request(null, null, THREE_WAY, invoiceLine("ITEM-A", "1", "100", null)))
                .lines().get(0).candidates()).isEmpty(); // không có số PO → tối đa 0.8750
    }

    // ------------------------------------------------------------------ truy vấn gửi sang BE-3

    @Test
    void shouldIssueSingleQueryWithNormalizedSelectors() {
        UUID hint = UUID.randomUUID();
        UUID poHint = UUID.randomUUID();
        finder.find(new CandidateSearchRequest(UUID.randomUUID(), SUPPLIER, ISSUE_DATE, "vnd", " po-2026-00007 ", poHint, THREE_WAY,
                List.of(invoiceLine(" item-a ", "1", "1", hint), invoiceLine("ITEM-A", "1", "1", null), invoiceLine("item-b", "1", "1", null))));

        assertThat(poQuery.calls).hasSize(1);
        PoLineCandidateQuery q = poQuery.calls.get(0);
        assertThat(q.supplierId()).isEqualTo(SUPPLIER);
        assertThat(q.orderDateTo()).isEqualTo(ISSUE_DATE);
        assertThat(q.currency()).isEqualTo("VND");
        assertThat(q.itemCodes()).containsExactlyInAnyOrder("ITEM-A", "ITEM-B");
        assertThat(q.poLineIds()).containsExactlyInAnyOrder(hint);
        assertThat(q.poNumber()).isEqualTo("PO-2026-00007");
        assertThat(q.purchaseOrderId()).isEqualTo(poHint);
        assertThat(q.limit()).isEqualTo(properties.getMaxScannedPoLines());
    }

    @Test
    void shouldNotQueryWhenInvoiceHasNoLines() {
        CandidateSearchResult result = finder.find(request(null, null, THREE_WAY));
        assertThat(result.lines()).isEmpty();
        assertThat(poQuery.calls).isEmpty();
    }

    // ------------------------------------------------------------------ công thức phụ

    @Test
    void shouldComputeShortageAndPricePercentages() {
        assertThat(CandidateFinder.shortagePct(bd("10"), bd("10"))).isEqualByComparingTo("0");
        assertThat(CandidateFinder.shortagePct(bd("10"), bd("50"))).isEqualByComparingTo("0");
        assertThat(CandidateFinder.shortagePct(bd("10"), bd("4"))).isEqualByComparingTo("60");
        assertThat(CandidateFinder.shortagePct(bd("10"), bd("0"))).isEqualByComparingTo("100");
        assertThat(CandidateFinder.priceVariancePct(bd("102"), bd("100"))).isEqualByComparingTo("2");
        assertThat(CandidateFinder.priceVariancePct(bd("98"), bd("100"))).isEqualByComparingTo("2");
        assertThat(CandidateFinder.priceVariancePct(bd("5"), bd("0"))).isEqualByComparingTo("100");
        assertThat(CandidateFinder.priceVariancePct(bd("0"), bd("0"))).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNonIncreasingScores(LineCandidates line) {
        for (int i = 1; i < line.candidates().size(); i++) {
            assertThat(line.candidates().get(i - 1).score().compareTo(line.candidates().get(i).score()) >= 0).isTrue();
        }
    }

    /** Đánh số dòng hóa đơn 1, 2, 3... theo thứ tự truyền vào. */
    private static CandidateSearchRequest request(String poRef, UUID poHint, ToleranceThreshold tol, InvoiceLineToMatch... lines) {
        List<InvoiceLineToMatch> numbered = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            InvoiceLineToMatch l = lines[i];
            numbered.add(new InvoiceLineToMatch(l.invoiceLineId(), i + 1, l.itemCode(), l.unmatchedQty(), l.unitPrice(), l.poLineIdHint()));
        }
        return new CandidateSearchRequest(UUID.randomUUID(), SUPPLIER, ISSUE_DATE, "VND", poRef, poHint, tol, numbered);
    }

    private static InvoiceLineToMatch invoiceLine(String itemCode, String qty, String price, UUID poLineHint) {
        return new InvoiceLineToMatch(UUID.randomUUID(), 1, itemCode, bd(qty), bd(price), poLineHint);
    }

    /** remaining = ordered (allocated = reserved = 0). */
    private static PoLineCandidateView poLine(String poNumber, LocalDate orderDate, int lineNo, String itemCode,
                                              String unitPrice, String orderedQty, String receivedQty) {
        return new PoLineCandidateView(UUID.nameUUIDFromBytes(poNumber.getBytes()), poNumber, SUPPLIER, orderDate,
                "OPEN", "VND", UUID.randomUUID(), lineNo, itemCode, bd(unitPrice), bd("0.1"),
                bd(orderedQty), bd("0"), bd("0"), bd(receivedQty));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static final class FakePurchaseOrderQueryApi implements PurchaseOrderQueryApi {
        final List<PoLineCandidateView> rows = new ArrayList<>();
        final List<PoLineCandidateQuery> calls = new ArrayList<>();

        @Override
        public List<PoLineCandidateView> findCandidateLines(PoLineCandidateQuery query) {
            calls.add(query);
            return List.copyOf(rows);
        }
    }
}
