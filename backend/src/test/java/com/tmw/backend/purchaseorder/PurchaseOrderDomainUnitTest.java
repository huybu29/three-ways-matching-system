package com.tmw.backend.purchaseorder;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.repository.PurchaseOrderRepository;
import com.tmw.backend.purchaseorder.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderDomainUnitTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    // ==================== 1. PURCHASE ORDER ====================

    @Test
    void shouldCreatePurchaseOrderAndRecalculateTotalAmountOnAddAndRemoveLine() {
        UUID supplierId = UUID.randomUUID();
        PurchaseOrder po = new PurchaseOrder("  po-2026-001 ", supplierId, LocalDate.of(2026, 11, 19), null);

        assertThat(po.getPoNumber()).isEqualTo("PO-2026-001");
        assertThat(po.getCurrency()).isEqualTo(CurrencyCode.VND);
        assertThat(po.getStatus()).isEqualTo(PoStatus.OPEN);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("0.0000");

        PurchaseOrderLine line1 = new PurchaseOrderLine(
                1, "item-001", "Laptop Dell",
                new BigDecimal("10"), new BigDecimal("20000000"), new BigDecimal("0.10")
        );
        PurchaseOrderLine line2 = new PurchaseOrderLine(
                2, "item-002", "Chuột không dây",
                new BigDecimal("5"), new BigDecimal("500000"), new BigDecimal("0.08")
        );

        po.addLine(line1);
        po.addLine(line2);

        assertThat(po.getLines()).hasSize(2);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("202500000.0000");

        po.removeLine(line2);
        assertThat(po.getLines()).hasSize(1);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("200000000.0000");
    }

    @Test
    void shouldRejectInvalidPurchaseOrderHeaderFields() {
        UUID supplierId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> new PurchaseOrder("   ", supplierId, today, CurrencyCode.VND))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PurchaseOrder("PO-001", null, today, CurrencyCode.VND))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PurchaseOrder("PO-001", supplierId, null, CurrencyCode.VND))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectDuplicateLineNoWithinSamePurchaseOrder() {
        PurchaseOrder po = new PurchaseOrder("PO-DUP-LINE", UUID.randomUUID(), LocalDate.now(), CurrencyCode.VND);
        PurchaseOrderLine line1 = new PurchaseOrderLine(1, "ITEM-A", "Hàng A", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);
        PurchaseOrderLine duplicateLineNo = new PurchaseOrderLine(1, "ITEM-B", "Hàng B", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO);

        po.addLine(line1);

        assertThatThrownBy(() -> po.addLine(duplicateLineNo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("line_no");
    }

    @Test
    void shouldExposeUnmodifiableLinesList() {
        PurchaseOrder po = new PurchaseOrder("PO-IMMUTABLE", UUID.randomUUID(), LocalDate.now(), CurrencyCode.VND);
        PurchaseOrderLine line = new PurchaseOrderLine(1, "ITEM-A", "Hàng A", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO);

        assertThatThrownBy(() -> po.getLines().add(line))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== 2. PURCHASE ORDER LINE ====================

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.05", "0.08", "0.10", "1"})
    void shouldAcceptVatRateInClosedRange0To1(String validVat) {
        PurchaseOrderLine line = new PurchaseOrderLine(
                1, "ITEM-VAT", "Test VAT",
                new BigDecimal("2.0000"), new BigDecimal("100000.0000"), new BigDecimal(validVat)
        );

        assertThat(line.getVatRate()).isEqualByComparingTo(validVat);
        assertThat(line.getLineAmount()).isEqualByComparingTo("200000.0000");
        assertThat(line.getVatAmount()).isEqualByComparingTo(
                new BigDecimal("200000.0000").multiply(new BigDecimal(validVat))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.0001", "-1", "1.0001", "8", "10"})
    void shouldRejectVatRateOutsideRange0To1(String invalidVat) {
        assertThatThrownBy(() -> new PurchaseOrderLine(
                1, "ITEM-VAT", "Test VAT lỗi",
                BigDecimal.TEN, new BigDecimal("10000"), new BigDecimal(invalidVat)
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("vat_rate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.0001", "-50"})
    void shouldRejectNonPositiveOrderedQty(String invalidQty) {
        assertThatThrownBy(() -> new PurchaseOrderLine(
                1, "ITEM-QTY", "Test SL lỗi",
                new BigDecimal(invalidQty), new BigDecimal("10000"), new BigDecimal("0.10")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("ordered_qty");
    }

    @Test
    void shouldRejectInvalidLineNoItemCodeOrNegativeUnitPrice() {
        assertThatThrownBy(() -> new PurchaseOrderLine(0, "ITEM-1", null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PurchaseOrderLine(1, "  ", null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PurchaseOrderLine(1, "ITEM-1", null, BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 3. EVENT PUBLISHING ====================

    @Test
    void shouldFlushPurchaseOrderBeforePublishingPurchaseOrderLineCreatedEvents() {
        UUID poId = UUID.randomUUID();
        UUID line1Id = UUID.randomUUID();
        UUID line2Id = UUID.randomUUID();

        PurchaseOrder po = new PurchaseOrder("PO-EVT-001", UUID.randomUUID(), LocalDate.now(), CurrencyCode.VND);
        PurchaseOrderLine line1 = new PurchaseOrderLine(1, "ITEM-1", "Hàng 1", new BigDecimal("10"), new BigDecimal("50000"), new BigDecimal("0.10"));
        PurchaseOrderLine line2 = new PurchaseOrderLine(2, "ITEM-2", "Hàng 2", new BigDecimal("5"), new BigDecimal("20000"), new BigDecimal("0.08"));
        po.addLine(line1);
        po.addLine(line2);

        when(purchaseOrderRepository.saveAndFlush(po)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(po, "id", poId);
            ReflectionTestUtils.setField(line1, "id", line1Id);
            ReflectionTestUtils.setField(line2, "id", line2Id);
            return po;
        });

        purchaseOrderService.createOrder(po);

        InOrder orderVerifier = inOrder(purchaseOrderRepository, eventPublisher);
        orderVerifier.verify(purchaseOrderRepository).saveAndFlush(po);

        ArgumentCaptor<PurchaseOrderLineCreated> eventCaptor = ArgumentCaptor.forClass(PurchaseOrderLineCreated.class);
        orderVerifier.verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<PurchaseOrderLineCreated> publishedEvents = eventCaptor.getAllValues();
        assertThat(publishedEvents).containsExactly(
                new PurchaseOrderLineCreated(poId, line1Id, new BigDecimal("10.0000"), new BigDecimal("500000.0000")),
                new PurchaseOrderLineCreated(poId, line2Id, new BigDecimal("5.0000"), new BigDecimal("100000.0000"))
        );
    }
}