package com.tmw.backend.invoice;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.invoice.api.InvoiceLifecycleApi;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import com.tmw.backend.invoice.repository.InvoiceRepository;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.testinfra.PostgresContainerBase;
import com.tmw.backend.testinfra.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InvoicePartialUniqueIndexIntegrationTest extends PostgresContainerBase {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLifecycleApi invoiceLifecycleApi;

    @Test
    void shouldRejectDuplicateActiveInvoiceButAllowReEntryAfterVoid() {
        Supplier supplier = supplierRepository.saveAndFlush(TestFixtures.supplier().build());
        String series = "1C26TAA";
        String number = String.format("%07d", TestFixtures.nextSeq());

        Invoice firstInvoice = buildInvoice(supplier.getId(), series, number);
        firstInvoice = invoiceRepository.saveAndFlush(firstInvoice);

        // 1. Tạo hóa đơn thứ 2 trùng (supplier_id, series, number) khi hóa đơn 1 chưa VOID -> lỗi DB unique index
        Invoice duplicateActive = buildInvoice(supplier.getId(), series, number);
        assertThatThrownBy(() -> invoiceRepository.saveAndFlush(duplicateActive))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 2. Chuyển hóa đơn 1 sang VOID -> cho phép tạo lại hóa đơn mới cùng bộ 3 khóa trên
        invoiceLifecycleApi.markVoid(firstInvoice.getId(), "Hủy hóa đơn sai thông tin để lập lại");

        Invoice reEnteredInvoice = invoiceRepository.saveAndFlush(buildInvoice(supplier.getId(), series, number));
        assertThat(reEnteredInvoice.getId()).isNotNull();
        assertThat(reEnteredInvoice.getId()).isNotEqualTo(firstInvoice.getId());
    }

    private Invoice buildInvoice(UUID supplierId, String series, String number) {
        Invoice invoice = new Invoice(
                supplierId,
                null,
                series,
                number,
                LocalDate.now(),
                CurrencyCode.VND,
                UUID.randomUUID()
        );
        invoice.addLine(new InvoiceLine(
                1,
                null,
                "ITEM-TEST",
                "Vật tư test unique index",
                new BigDecimal("10.0000"),
                new BigDecimal("20000.0000"),
                new BigDecimal("0.1000")
        ));
        return invoice;
    }
}