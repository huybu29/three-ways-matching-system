package com.tmw.backend.audit;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.invoice.api.InvoiceLifecycleApi;
import com.tmw.backend.masterdata.dto.SupplierRequest;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.service.SupplierService;
import com.tmw.backend.testinfra.PostgresContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogImmutabilityIntegrationTest extends PostgresContainerBase {

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private InvoiceLifecycleApi invoiceLifecycleApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCaptureAuditSnapshotOnCreateSupplier() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Supplier created = supplierService.create(new SupplierRequest(
                "SUP_" + suffix,
                "Nhà cung cấp " + suffix,
                "031" + (long) (Math.random() * 1_000_000_0L),
                "TP. Hồ Chí Minh"
        ));

        var rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE entity_id = ?",
                created.getId()
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("CREATE");
    }

    @Test
    void shouldRollbackAuditLogWhenBusinessActionFails() {
        long beforeCount = countAuditLogs();

        assertThatThrownBy(() -> invoiceLifecycleApi.markVoid(UUID.randomUUID(), "Hóa đơn không tồn tại"))
                .isInstanceOf(BusinessException.class);

        assertThat(countAuditLogs()).isEqualTo(beforeCount);
    }

    private long countAuditLogs() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        return count == null ? 0L : count;
    }
}