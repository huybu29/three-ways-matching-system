package com.tmw.backend.matching;

import com.tmw.backend.testinfra.PostgresContainerBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra ràng buộc DB của module matching trên PostgreSQL thật (Testcontainers của PostgresContainerBase).
 *
 * <p>Chạy trực tiếp file migration của BE-5 trên một schema riêng cho mỗi test, nên không phụ thuộc
 * vào việc Flyway đã được cấu hình cho toàn dự án hay migration của các module khác đã đầy đủ chưa.
 * Khi Tech Lead bật Flyway + ddl-auto=validate, chính file migration này sẽ được Flyway chạy.</p>
 */
class MatchPartialUniqueIndexIntegrationTest extends PostgresContainerBase {

    private static final String MIGRATION = "/db/migration/V202611270140__matching_create_match_tables.sql";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    private Connection connection;
    private String schema;

    @BeforeEach
    void createIsolatedSchemaAndRunMigration() throws Exception {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        schema = "be5_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE SCHEMA " + schema);
            st.execute("SET search_path TO " + schema);
            st.execute(readMigration());
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } finally {
            connection.close();
        }
    }

    // ------------------------------------------------------------------ uk_match_active_invoice

    @Test
    void shouldAllowOnlyOneActiveMatchPerInvoice() throws SQLException {
        UUID invoiceId = UUID.randomUUID();
        insertMatch(invoiceId, "PROPOSED");

        assertSqlState(() -> insertMatch(invoiceId, "PROPOSED"), UNIQUE_VIOLATION);
        assertSqlState(() -> insertMatch(invoiceId, "APPROVED"), UNIQUE_VIOLATION);
    }

    @Test
    void shouldAllowNewActiveMatchAfterPreviousOneRejectedOrCancelled() throws SQLException {
        UUID invoiceId = UUID.randomUUID();
        UUID first = insertMatch(invoiceId, "PROPOSED");

        updateStatus(first, "REJECTED", "Sai đơn giá dòng 2");
        UUID second = insertMatch(invoiceId, "PROPOSED");

        updateStatus(second, "CANCELLED", "Matcher hủy để ghép lại");
        assertThatCode(() -> insertMatch(invoiceId, "PROPOSED")).doesNotThrowAnyException();
    }

    @Test
    void shouldNotLimitTerminalMatchesOrOtherInvoices() {
        UUID invoiceId = UUID.randomUUID();
        assertThatCode(() -> {
            insertMatch(invoiceId, "POSTED");
            insertMatch(invoiceId, "REJECTED");
            insertMatch(invoiceId, "CANCELLED");
            insertMatch(invoiceId, "PROPOSED");
            insertMatch(UUID.randomUUID(), "PROPOSED");
        }).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ check constraints

    @Test
    void shouldEnforceConfidenceBySource() {
        assertSqlState(() -> insertMatch(UUID.randomUUID(), "PROPOSED", "MANUAL", "0.5000"), CHECK_VIOLATION);
        assertSqlState(() -> insertMatch(UUID.randomUUID(), "PROPOSED", "AUTO", null), CHECK_VIOLATION);
        assertThatCode(() -> insertMatch(UUID.randomUUID(), "PROPOSED", "MANUAL", null)).doesNotThrowAnyException();
    }

    @Test
    void shouldRequireReasonWhenRejectedOrCancelled() throws SQLException {
        UUID matchId = insertMatch(UUID.randomUUID(), "PROPOSED");
        assertSqlState(() -> updateStatus(matchId, "REJECTED", null), CHECK_VIOLATION);
        assertSqlState(() -> updateStatus(matchId, "CANCELLED", null), CHECK_VIOLATION);
    }

    @Test
    void shouldEnforceMatchLineUniquenessAndPositiveQty() throws SQLException {
        UUID matchId = insertMatch(UUID.randomUUID(), "PROPOSED");
        UUID invoiceLineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();

        insertLine(matchId, 1, invoiceLineId, poLineId, "5");

        assertSqlState(() -> insertLine(matchId, 2, invoiceLineId, poLineId, "3"), UNIQUE_VIOLATION);
        assertSqlState(() -> insertLine(matchId, 1, UUID.randomUUID(), UUID.randomUUID(), "3"), UNIQUE_VIOLATION);
        assertSqlState(() -> insertLine(matchId, 3, UUID.randomUUID(), UUID.randomUUID(), "0"), CHECK_VIOLATION);
    }

    // ------------------------------------------------------------------ helpers

    private UUID insertMatch(UUID invoiceId, String status) throws SQLException {
        return insertMatch(invoiceId, status, "AUTO", "0.9000");
    }

    private UUID insertMatch(UUID invoiceId, String status, String source, String confidence) throws SQLException {
        UUID id = UUID.randomUUID();
        boolean approved = status.equals("APPROVED") || status.equals("POSTED");
        boolean posted = status.equals("POSTED");
        boolean terminal = status.equals("REJECTED") || status.equals("CANCELLED");
        Timestamp now = Timestamp.from(Instant.now());

        String sql = """
                INSERT INTO match (id, invoice_id, status, source, allocation_state, overall_confidence,
                                   proposed_by, approved_by, approved_at, posted_by, posted_at, reject_reason,
                                   created_at, updated_at)
                VALUES (?, ?, ?, ?, 'NONE', ?::numeric, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, invoiceId);
            ps.setString(3, status);
            ps.setString(4, source);
            ps.setString(5, confidence);
            ps.setObject(6, UUID.randomUUID());
            ps.setObject(7, approved ? UUID.randomUUID() : null);
            ps.setTimestamp(8, approved ? now : null);
            ps.setObject(9, posted ? UUID.randomUUID() : null);
            ps.setTimestamp(10, posted ? now : null);
            ps.setString(11, terminal ? "Lý do kiểm thử" : null);
            ps.setTimestamp(12, now);
            ps.setTimestamp(13, now);
            ps.executeUpdate();
        }
        return id;
    }

    private void updateStatus(UUID matchId, String status, String reason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE match SET status = ?, reject_reason = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, reason);
            ps.setObject(3, matchId);
            ps.executeUpdate();
        }
    }

    private void insertLine(UUID matchId, int lineNo, UUID invoiceLineId, UUID poLineId, String qty) throws SQLException {
        String sql = """
                INSERT INTO match_line (id, match_id, line_no, invoice_line_id, po_id, po_line_id, match_type,
                                        matched_qty, invoice_unit_price, po_unit_price, matched_amount,
                                        allocation_state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'TWO_WAY', ?::numeric, 10, 10, 50, 'NONE', now(), now())
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, matchId);
            ps.setInt(3, lineNo);
            ps.setObject(4, invoiceLineId);
            ps.setObject(5, UUID.randomUUID());
            ps.setObject(6, poLineId);
            ps.setString(7, qty);
            ps.executeUpdate();
        }
    }

    private static void assertSqlState(SqlAction action, String expectedState) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(SQLException.class, ex ->
                        assertThat(ex.getSQLState()).isEqualTo(expectedState));
    }

    private static String readMigration() throws IOException {
        try (InputStream in = MatchPartialUniqueIndexIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("Không tìm thấy migration %s trên classpath", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
