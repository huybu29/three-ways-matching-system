package com.tmw.backend.purchaseorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderLineResponse(
        UUID id,
        @JsonProperty("line_no") Integer lineNo,
        @JsonProperty("item_code") String itemCode,
        String description,
        @JsonProperty("ordered_qty") BigDecimal orderedQty,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("vat_rate") BigDecimal vatRate,
        @JsonProperty("line_amount") BigDecimal lineAmount,
        @JsonProperty("vat_amount") BigDecimal vatAmount,
        CapacitySnapshot capacity
) {
    public static PurchaseOrderLineResponse from(PurchaseOrderLine line, CapacitySnapshot capacity) {
        return new PurchaseOrderLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getItemCode(),
                line.getDescription(),
                line.getOrderedQty(),
                line.getUnitPrice(),
                line.getVatRate(),
                line.getLineAmount(),
                line.getVatAmount(),
                capacity
        );
    }
}
