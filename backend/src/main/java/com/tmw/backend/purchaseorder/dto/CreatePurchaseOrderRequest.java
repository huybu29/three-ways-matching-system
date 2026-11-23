package com.tmw.backend.purchaseorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.purchaseorder.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotBlank(message = "Số đơn đặt hàng (po_number) không được để trống")
        @Size(max = 50, message = "Số đơn đặt hàng tối đa 50 ký tự")
        @JsonProperty("po_number")
        String poNumber,

        @NotNull(message = "Nhà cung cấp (supplier_id) không được để trống")
        @JsonProperty("supplier_id")
        UUID supplierId,

        @NotNull(message = "Ngày đặt hàng (order_date) không được để trống")
        @JsonProperty("order_date")
        LocalDate orderDate,

        CurrencyCode currency,

        @NotEmpty(message = "Đơn đặt hàng phải có ít nhất 1 dòng hàng")
        @Valid
        List<PurchaseOrderLineRequest> lines
) {
}