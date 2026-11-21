package com.tmw.backend.invoice;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceLifecycleSnapshot;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import com.tmw.backend.invoice.repository.InvoiceRepository;
import com.tmw.backend.invoice.service.InvoiceLifecycleServiceImpl;
import com.tmw.backend.invoice.service.InvoiceStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDomainAndLifecycleUnitTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceLifecycleServiceImpl lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new InvoiceLifecycleServiceImpl(invoiceRepository, new InvoiceStateMachine());
    }

    // ==================== 1. INVOICE & INVOICE LINE INVARIANTS ====================

    @Test
    void shouldEnforceMatchedQtyBetween0AndQtyOnInvoiceLine() {
        InvoiceLine line = new InvoiceLine(
                1,
                UUID.randomUUID(),
                "item-001",
                "Thiết bị mạng",
                new BigDecimal("50.0000"),
                new BigDecimal("1000000.0000"),
                new BigDecimal("0.1000")
        );

        assertThat(line.getMatchedQty()).isEqualByComparingTo("0.0000");
        assertThat(line.getUnmatchedQty()).isEqualByComparingTo("50.0000");

        // Cộng dồn chạm đúng biên qty = 50
        line.addMatchedQty(new BigDecimal("30.0000"));
        line.addMatchedQty(new BigDecimal("20.0000"));
        assertThat(line.getMatchedQty()).isEqualByComparingTo("50.0000");
        assertThat(line.getUnmatchedQty()).isEqualByComparingTo("0.0000");

        // Vượt quá qty -> ném VALIDATION_ERROR
        assertThatThrownBy(() -> line.addMatchedQty(new BigDecimal("0.0001")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        // Giải phóng một phần về 15
        line.releaseMatchedQty(new BigDecimal("35.0000"));
        assertThat(line.getMatchedQty()).isEqualByComparingTo("15.0000");

        // Giải phóng vượt quá matched_qty (xuống âm) -> ném VALIDATION_ERROR
        assertThatThrownBy(() -> line.releaseMatchedQty(new BigDecimal("15.0001")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        // Reset về 0
        line.resetMatchedQty();
        assertThat(line.getMatchedQty()).isEqualByComparingTo("0.0000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.0001"})
    void shouldRejectNonPositiveDeltaOnMatchedQty(String invalidDelta) {
        InvoiceLine line = new InvoiceLine(
                1, UUID.randomUUID(), "ITEM-1", null,
                new BigDecimal("10"), new BigDecimal("1000"), BigDecimal.ZERO
        );

        assertThatThrownBy(() -> line.addMatchedQty(new BigDecimal(invalidDelta)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> line.releaseMatchedQty(new BigDecimal(invalidDelta)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCalculateInvoiceTotalsAndRejectDuplicateLineNo() {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "  1c26taa ",
                " 000999 ",
                LocalDate.of(2026, 11, 19),
                CurrencyCode.VND,
                UUID.randomUUID()
        );

        assertThat(invoice.getInvoiceSeries()).isEqualTo("1C26TAA");
        assertThat(invoice.getInvoiceNumber()).isEqualTo("000999");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);

        InvoiceLine line1 = new InvoiceLine(1, UUID.randomUUID(), "ITEM-1", "Hàng 1", new BigDecimal("2"), new BigDecimal("100000"), new BigDecimal("0.10"));
        InvoiceLine line2 = new InvoiceLine(2, UUID.randomUUID(), "ITEM-2", "Hàng 2", new BigDecimal("3"), new BigDecimal("50000"), new BigDecimal("0.08"));
        InvoiceLine duplicateLineNo = new InvoiceLine(1, UUID.randomUUID(), "ITEM-3", "Trùng dòng", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO);

        invoice.addLine(line1);
        invoice.addLine(line2);

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("350000.0000");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("32000.0000");
        assertThat(invoice.getTotal()).isEqualByComparingTo("382000.0000");

        assertThatThrownBy(() -> invoice.addLine(duplicateLineNo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("line_no");
    }

    // ==================== 2. INVOICE LIFECYCLE API ====================

    @Test
    void shouldExecuteFullInvoiceLifecycleAndResetViaLifecycleApi() {
        UUID invoiceId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID posterId = UUID.randomUUID();

        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1C26TAA",
                "0000888",
                LocalDate.now(),
                CurrencyCode.VND,
                UUID.randomUUID()
        );
        InvoiceLine line = new InvoiceLine(1, UUID.randomUUID(), "ITEM-1", "Hàng 1", new BigDecimal("10"), new BigDecimal("10000"), new BigDecimal("0.10"));
        invoice.addLine(line);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        ReflectionTestUtils.setField(invoice, "version", 0L);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findWithLinesById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> {
            Invoice saved = inv.getArgument(0);
            Long currentVer = saved.getVersion() != null ? saved.getVersion() : 0L;
            ReflectionTestUtils.setField(saved, "version", currentVer + 1);
            return saved;
        });

        // 1. RECEIVED -> MATCHING
        InvoiceLifecycleSnapshot s1 = lifecycleService.markMatching(invoiceId);
        assertThat(s1.status()).isEqualTo(InvoiceStatus.MATCHING);
        assertThat(s1.version()).isEqualTo(1L);

        // 2. MATCHING -> EXCEPTION
        line.addMatchedQty(new BigDecimal("10"));
        InvoiceLifecycleSnapshot s2 = lifecycleService.markException(invoiceId, "Lệch đơn giá 3%");
        assertThat(s2.status()).isEqualTo(InvoiceStatus.EXCEPTION);
        assertThat(s2.statusReason()).isEqualTo("Lệch đơn giá 3%");

        // 3. Reset về RECEIVED -> matched_qty về 0
        InvoiceLifecycleSnapshot s3 = lifecycleService.resetToReceived(invoiceId);
        assertThat(s3.status()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(s3.statusReason()).isNull();
        assertThat(line.getMatchedQty()).isEqualByComparingTo("0.0000");

        // 4. RECEIVED -> MATCHING -> MATCHED -> APPROVED -> POSTED
        lifecycleService.markMatching(invoiceId);
        lifecycleService.markMatched(invoiceId);
        InvoiceLifecycleSnapshot sApproved = lifecycleService.markApproved(invoiceId, approverId, null);
        assertThat(sApproved.status()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(sApproved.approvedBy()).isEqualTo(approverId);

        InvoiceLifecycleSnapshot sPosted = lifecycleService.markPosted(invoiceId, posterId);
        assertThat(sPosted.status()).isEqualTo(InvoiceStatus.POSTED);
        assertThat(sPosted.postedBy()).isEqualTo(posterId);

        verify(invoiceRepository, org.mockito.Mockito.times(8)).saveAndFlush(invoice);
    }
}