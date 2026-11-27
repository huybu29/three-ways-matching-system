package com.tmw.backend.matching.candidate;

import com.tmw.backend.matching.candidate.CandidateSearchRequest.InvoiceLineToMatch;
import com.tmw.backend.matching.scoring.ConfidenceScorer;
import com.tmw.backend.matching.scoring.ConfidenceWeightProperties;
import com.tmw.backend.matching.variance.ToleranceThreshold;
import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import com.tmw.backend.purchaseorder.api.PoLineCandidateView;
import com.tmw.backend.purchaseorder.service.JdbcPurchaseOrderQueryService;
import com.tmw.backend.testinfra.PostgresContainerBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đo CandidateFinder + JdbcPurchaseOrderQueryService trên PostgreSQL thật với 1.000 PO / 5.000 dòng
 * (task giai đoạn 2 của BE-5). Dựng schema riêng từ bản sao bảng của BE-3 (test resource), nạp dữ liệu
 * tất định, áp index của R__matching_candidate_finder_indexes.sql.
 */
class CandidateFinderPerformanceIntegrationTest extends PostgresContainerBase {

    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 11, 27);
    private static final ToleranceThreshold POLICY = new ToleranceThreshold(
            new BigDecimal("2.0000"), new BigDecimal("1.0000"), null, true);
    /** Ngưỡng nới rộng cho máy CI; đo thực tế trên PostgreSQL 16 khoảng 1 ms (xem docs/be5). */
    private static final double P95_BUDGET_MS = 50.0;

    private static String schema;
    private static SingleConnectionDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static JdbcPurchaseOrderQueryService poQuery;
    private static CandidateFinder finder;

    @BeforeAll
    static void createSchemaSeedAndIndexes() throws IOException {
        schema = "be5_perf_" + UUID.randomUUID().toString().replace("-", "");
        SingleConnectionDataSource adminDs = new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        new JdbcTemplate(adminDs).execute("CREATE SCHEMA " + schema);
        adminDs.destroy();

        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        dataSource = new SingleConnectionDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcTemplate plain = jdbc.getJdbcTemplate();
        plain.execute(read("matching/candidate-finder-schema.sql"));
        plain.execute(read("matching/candidate-finder-seed-1000-po.sql"));
        plain.execute(read("db/migration/R__matching_candidate_finder_indexes.sql"));
        plain.execute("ANALYZE");

        ConfidenceWeightProperties weights = new ConfidenceWeightProperties();
        weights.validateWeights();
        CandidateFinderProperties props = new CandidateFinderProperties();
        props.validate();
        poQuery = new JdbcPurchaseOrderQueryService(jdbc);
        finder = new CandidateFinder(poQuery, new ConfidenceScorer(weights), props);
    }

    @AfterAll
    static void dropSchema() {
        if (jdbc != null) {
            jdbc.getJdbcTemplate().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void seedShouldContain1000PurchaseOrders() {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM purchase_order", Integer.class);
        assertThat(count).isEqualTo(1000);
    }

    @Test
    void shouldReturnOnlyOpenLinesOfSupplierWithRemainingCapacityInFifoOrder() {
        UUID supplier = supplier(1);
        List<String> codes = itemCodesOf(supplier);
        List<PoLineCandidateView> rows = poQuery.findCandidateLines(new PoLineCandidateQuery(
                supplier, ISSUE_DATE, "VND", Set.copyOf(codes), Set.of(), null, null, 500));

        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.supplierId().equals(supplier));
        assertThat(rows).allMatch(r -> r.status().equals("OPEN") || r.status().equals("PARTIALLY_INVOICED"));
        assertThat(rows).allMatch(r -> r.remainingQty().signum() > 0);
        assertThat(rows).allMatch(r -> !r.orderDate().isAfter(ISSUE_DATE));
        assertThat(rows).allMatch(r -> codes.contains(r.itemCode()));
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i - 1).orderDate().isAfter(rows.get(i).orderDate())).isFalse();
        }
    }

    @Test
    void shouldFindLinesByPoNumberOrPoIdWithoutItemCodes() {
        // PO 1 (OPEN) thuộc nhà cung cấp 1
        List<PoLineCandidateView> byNumber = poQuery.findCandidateLines(new PoLineCandidateQuery(
                supplier(1), ISSUE_DATE, "VND", Set.of(), Set.of(), "po-2026-00001", null, 500));
        assertThat(byNumber).isNotEmpty();
        assertThat(byNumber).allMatch(r -> r.poNumber().equals("PO-2026-00001"));

        UUID poId = byNumber.get(0).purchaseOrderId();
        List<PoLineCandidateView> byId = poQuery.findCandidateLines(new PoLineCandidateQuery(
                supplier(1), ISSUE_DATE, "VND", Set.of(), Set.of(), null, poId, 500));
        assertThat(byId).hasSize(byNumber.size());
    }

    @Test
    void shouldUseSupplierIndexInQueryPlan() {
        UUID supplier = supplier(7);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierId", supplier)
                .addValue("orderDateTo", ISSUE_DATE)
                .addValue("currency", "VND")
                .addValue("itemCodes", itemCodesOf(supplier).subList(0, 3))
                .addValue("poLineIds", List.of(new UUID(0, 0)))
                .addValue("poNumber", null, java.sql.Types.VARCHAR)
                .addValue("purchaseOrderId", null, java.sql.Types.OTHER)
                .addValue("limit", 500);
        String plan = String.join("\n", jdbc.queryForList(
                "EXPLAIN " + readUnchecked("sql/purchaseorder/find-candidate-po-lines.sql"), params, String.class));

        assertThat(plan).contains("idx_purchase_order_supplier_status_date");
    }

    @Test
    void candidateSearchFor300InvoicesShouldStayWithinBudget() {
        Random random = new Random(42);
        List<CandidateSearchRequest> workload = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            UUID supplier = supplier(1 + random.nextInt(50));
            List<String> codes = new ArrayList<>(itemCodesOf(supplier));
            Collections.shuffle(codes, random);
            List<InvoiceLineToMatch> lines = new ArrayList<>();
            for (int l = 0; l < 3; l++) {
                lines.add(new InvoiceLineToMatch(UUID.randomUUID(), l + 1, codes.get(l),
                        BigDecimal.valueOf(1 + random.nextInt(20)), BigDecimal.valueOf(1000L * (1 + random.nextInt(50))), null));
            }
            workload.add(new CandidateSearchRequest(UUID.randomUUID(), supplier, ISSUE_DATE, "VND",
                    null, null, POLICY, lines));
        }
        workload.subList(0, 20).forEach(finder::find); // làm nóng

        List<Double> millis = new ArrayList<>();
        int found = 0;
        for (CandidateSearchRequest request : workload) {
            long start = System.nanoTime();
            CandidateSearchResult result = finder.find(request);
            millis.add((System.nanoTime() - start) / 1_000_000.0);
            found += (int) result.lines().stream().filter(l -> !l.candidates().isEmpty()).count();
        }
        Collections.sort(millis);
        double p50 = millis.get(millis.size() / 2);
        double p95 = millis.get((int) (millis.size() * 0.95));
        System.out.printf("CandidateFinder 1.000 PO: p50=%.2f ms, p95=%.2f ms, max=%.2f ms, %d/%d dòng HĐ có ứng viên%n",
                p50, p95, millis.get(millis.size() - 1), found, workload.size() * 3);

        assertThat(found).isGreaterThan(0);
        assertThat(p95 < P95_BUDGET_MS).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private static UUID supplier(int s) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", s));
    }

    private static List<String> itemCodesOf(UUID supplier) {
        return jdbc.getJdbcTemplate().queryForList(
                "SELECT DISTINCT pl.item_code FROM po_line pl JOIN purchase_order po ON po.id = pl.purchase_order_id "
                        + "WHERE po.supplier_id = ? ORDER BY 1", String.class, supplier);
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readUnchecked(String resource) {
        try {
            return read(resource);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
