package com.tmw.backend.purchaseorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(
        @NotNull(message = "Số thứ tự dòng (line_no) không được để trống")
        @Min(value = 1, message = "Số thứ tự dòng (line_no) phải lớn hơn 0")
        @JsonProperty("line_no")
        Integer lineNo,

        @NotBlank(message = "Mã hàng hóa (item_code) không được để trống")
        @Size(max = 100, message = "Mã hàng hóa tối đa 100 ký tự")
        @JsonProperty("item_code")
        String itemCode,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @NotNull(message = "Số lượng đặt (ordered_qty) không được để trống")
        @DecimalMin(value = "0.0001", message = "Số lượng đặt (ordered_qty) phải lớn hơn 0")
        @JsonProperty("ordered_qty")
        BigDecimal orderedQty,

        @NotNull(message = "Đơn giá (unit_price) không được để trống")
        @DecimalMin(value = "0.0000", message = "Đơn giá (unit_price) không được âm")
        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        @DecimalMin(value = "0.0000", message = "Thuế suất (vat_rate) không được âm")
        @DecimalMax(value = "1.0000", message = "Thuế suất (vat_rate) tối đa là 1.0000")
        @JsonProperty("vat_rate")
        BigDecimal vatRate
) {
}