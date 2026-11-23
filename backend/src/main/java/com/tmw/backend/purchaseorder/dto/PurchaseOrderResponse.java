package com.tmw.backend.purchaseorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.purchaseorder.CurrencyCode;
import com.tmw.backend.purchaseorder.PoStatus;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        @JsonProperty("po_number") String poNumber,
        @JsonProperty("supplier_id") UUID supplierId,
        @JsonProperty("order_date") LocalDate orderDate,
        CurrencyCode currency,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        PoStatus status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<PurchaseOrderLineResponse> lines
) {
    public static PurchaseOrderResponse from(PurchaseOrder po, Map<UUID, CapacitySnapshot> capacityMap) {
        Map<UUID, CapacitySnapshot> safeMap = capacityMap != null ? capacityMap : Collections.emptyMap();
        List<PurchaseOrderLineResponse> lineResponses = po.getLines().stream()
                .map(line -> PurchaseOrderLineResponse.from(line, safeMap.get(line.getId())))
                .toList();

        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplierId(),
                po.getOrderDate(),
                po.getCurrency(),
                po.getTotalAmount(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                lineResponses
        );
    }

    public static PurchaseOrderResponse summaryFrom(PurchaseOrder po) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplierId(),
                po.getOrderDate(),
                po.getCurrency(),
                po.getTotalAmount(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                null
        );
    }
}
