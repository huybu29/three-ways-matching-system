package com.tmw.backend.purchaseorder;

import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import com.tmw.backend.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.tmw.backend.purchaseorder.dto.PurchaseOrderLineRequest;
import com.tmw.backend.purchaseorder.dto.PurchaseOrderResponse;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.repository.PurchaseOrderRepository;
import com.tmw.backend.purchaseorder.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceUnitTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private PoLineCapacityRepository capacityRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private UUID supplierId;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
    }

    @Test
    void shouldCreatePurchaseOrderAndPublishLineCreatedEvents() {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest(
                "po-2026-001",
                supplierId,
                LocalDate.of(2026, 11, 23),
                CurrencyCode.VND,
                List.of(
                        new PurchaseOrderLineRequest(
                                1,
                                "ITEM-A",
                                "Laptop Dell",
                                new BigDecimal("2"),
                                new BigDecimal("15000000"),
                                new BigDecimal("0.10")
                        )
                )
        );

        when(purchaseOrderRepository.existsByPoNumberIgnoreCase("PO-2026-001")).thenReturn(false);
        when(supplierRepository.existsById(supplierId)).thenReturn(true);
        when(purchaseOrderRepository.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            UUID poId = UUID.randomUUID();
            ReflectionTestUtils.setField(po, "id", poId);
            for (PurchaseOrderLine line : po.getLines()) {
                ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
            }
            return po;
        });
        when(capacityRepository.findAllByPoLineIdIn(any())).thenReturn(List.of());

        PurchaseOrderResponse response = purchaseOrderService.create(request);

        assertEquals("PO-2026-001", response.poNumber());
        assertEquals(PoStatus.OPEN, response.status());
        assertEquals(0, new BigDecimal("30000000.0000").compareTo(response.totalAmount()));
        verify(eventPublisher, times(1)).publishEvent(any(PurchaseOrderLineCreated.class));
    }

    @Test
    void shouldRejectDuplicatePoNumber() {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest(
                "PO-2026-001",
                supplierId,
                LocalDate.now(),
                CurrencyCode.VND,
                List.of(new PurchaseOrderLineRequest(1, "ITEM-A", null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO))
        );

        when(purchaseOrderRepository.existsByPoNumberIgnoreCase("PO-2026-001")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> purchaseOrderService.create(request));
        assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
        verify(purchaseOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectWhenSupplierNotFound() {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest(
                "PO-2026-002",
                supplierId,
                LocalDate.now(),
                CurrencyCode.VND,
                List.of(new PurchaseOrderLineRequest(1, "ITEM-A", null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO))
        );

        when(purchaseOrderRepository.existsByPoNumberIgnoreCase("PO-2026-002")).thenReturn(false);
        when(supplierRepository.existsById(supplierId)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> purchaseOrderService.create(request));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldPreventClosingOrderWhenActiveReservationExists() {
        UUID poId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        PurchaseOrder po = new PurchaseOrder("PO-2026-003", supplierId, LocalDate.now(), CurrencyCode.VND);
        ReflectionTestUtils.setField(po, "id", poId);
        PurchaseOrderLine line = new PurchaseOrderLine(1, "ITEM-B", null, new BigDecimal("10"), new BigDecimal("100"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(line, "id", lineId);
        po.addLine(line);

        PoLineCapacity cap = mock(PoLineCapacity.class);
        when(cap.getReservedQty()).thenReturn(new BigDecimal("2.0000"));

        when(purchaseOrderRepository.findWithLinesById(poId)).thenReturn(Optional.of(po));
        when(capacityRepository.findAllByPoLineIdIn(List.of(lineId))).thenReturn(List.of(cap));

        BusinessException ex = assertThrows(BusinessException.class, () -> purchaseOrderService.close(poId));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, ex.getErrorCode());
    }
}