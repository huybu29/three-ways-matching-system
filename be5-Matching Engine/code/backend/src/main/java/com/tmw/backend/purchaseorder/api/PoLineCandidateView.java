package com.tmw.backend.purchaseorder.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một dòng PO ứng viên kèm số liệu hạn mức tại thời điểm đọc (chưa khóa dòng).
 * Số liệu chỉ để xếp hạng; giữ chỗ thật vẫn phải qua CapacityAllocationService (khóa po_line_capacity).
 */
public record PoLineCandidateView(
        UUID purchaseOrderId,
        String poNumber,
        UUID supplierId,
        LocalDate orderDate,
        String status,
        String currency,
        UUID poLineId,
        int lineNo,
        String itemCode,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal orderedQty,
        BigDecimal allocatedQty,
        BigDecimal reservedQty,
        BigDecimal receivedQty
) {

    /** Vế PO: ordered - allocated - reserved. */
    public BigDecimal remainingQty() {
        return orderedQty.subtract(allocatedQty).subtract(reservedQty);
    }

    /** Vế GRN: received - allocated - reserved, không âm (số lượng đã nhận kho mà chưa ghép). */
    public BigDecimal grnRemainingQty() {
        BigDecimal value = receivedQty.subtract(allocatedQty).subtract(reservedQty);
        return value.signum() < 0 ? BigDecimal.ZERO.setScale(value.scale()) : value;
    }
}
