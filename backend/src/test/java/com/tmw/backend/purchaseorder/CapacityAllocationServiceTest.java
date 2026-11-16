package com.tmw.backend.purchaseorder;

import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.service.CapacityAllocationService;
import com.tmw.backend.purchaseorder.service.CapacityAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CapacityAllocationServiceTest {

    private CapacityAllocationService capacityService;
    private PoLineCapacity capacity;

    @BeforeEach
    void setUp() {
        capacityService = new CapacityAllocationServiceImpl();
        PurchaseOrderLine line = new PurchaseOrderLine();
        BigDecimal orderedQty = new BigDecimal("10.0000");
        BigDecimal orderedAmount = new BigDecimal("1000000.0000");
        capacity = new PoLineCapacity(line, orderedQty, orderedAmount);
    }

    @Test
    @DisplayName("Bất biến ban đầu: remaining = ordered khi chưa có ai giữ chỗ")
    void testInitialCapacity() {
        assertEquals(new BigDecimal("10.0000"), capacity.getRemainingQty());
        assertEquals(new BigDecimal("1000000.0000"), capacity.getRemainingAmount());
    }

    @Test
    @DisplayName("Giữ chỗ thành công trong phạm vi hạn mức")
    void testReserveCapacitySuccess() {
        BigDecimal reserveQty = new BigDecimal("4.0000");
        BigDecimal reserveAmount = new BigDecimal("400000.0000");

        capacityService.reserveCapacity(capacity, reserveQty, reserveAmount);

        assertEquals(new BigDecimal("6.0000"), capacity.getRemainingQty());
        assertEquals(new BigDecimal("600000.0000"), capacity.getRemainingAmount());
        assertEquals(reserveQty, capacity.getReservedQty());
    }

    @Test
    @DisplayName("Ném ngoại lệ khi giữ chỗ vượt quá hạn mức còn lại (Invariant violation)")
    void testReserveCapacityExceeded() {
        BigDecimal exceedQty = new BigDecimal("11.0000");
        BigDecimal exceedAmount = new BigDecimal("1100000.0000");

        assertThrows(IllegalStateException.class, () -> {
            capacityService.reserveCapacity(capacity, exceedQty, exceedAmount);
        });
    }

    @Test
    @DisplayName("Chuyển từ giữ chỗ (reserved) sang phân bổ chính thức (allocated)")
    void testConfirmAllocation() {
        BigDecimal qty = new BigDecimal("5.0000");
        BigDecimal amount = new BigDecimal("500000.0000");

        capacityService.reserveCapacity(capacity, qty, amount);
        capacityService.confirmAllocation(capacity, qty, amount);

        assertEquals(BigDecimal.ZERO.setScale(4), capacity.getReservedQty());
        assertEquals(qty, capacity.getAllocatedQty());
        assertEquals(new BigDecimal("5.0000"), capacity.getRemainingQty());
    }

    @Test
    @DisplayName("Hoàn lại phần đã giữ chỗ (release)")
    void testReleaseReservation() {
        BigDecimal qty = new BigDecimal("3.0000");
        BigDecimal amount = new BigDecimal("300000.0000");

        capacityService.reserveCapacity(capacity, qty, amount);
        capacityService.releaseReservation(capacity, qty, amount);

        assertEquals(BigDecimal.ZERO.setScale(4), capacity.getReservedQty());
        assertEquals(new BigDecimal("10.0000"), capacity.getRemainingQty());
    }
}