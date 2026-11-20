package com.tmw.backend.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.common.validation.ValidMoney;
import com.tmw.backend.common.validation.ValidQuantity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TolerancePolicyRequest(
        @NotBlank(message = "Tên chính sách dung sai không được để trống")
        @Size(max = 100, message = "Tên chính sách tối đa 100 ký tự")
        String name,

        @JsonProperty("supplier_id")
        UUID supplierId,

        @JsonProperty("item_category")
        @Size(max = 100, message = "Nhóm hàng tối đa 100 ký tự")
        String itemCategory,

        @JsonProperty("min_amount")
        @ValidMoney
        String minAmount,

        @JsonProperty("max_amount")
        @ValidMoney
        String maxAmount,

        @JsonProperty("qty_tolerance_pct")
        @NotBlank(message = "Tỷ lệ dung sai số lượng không được để trống")
        @ValidQuantity
        String qtyTolerancePct,

        @JsonProperty("price_tolerance_pct")
        @NotBlank(message = "Tỷ lệ dung sai giá không được để trống")
        @ValidQuantity
        String priceTolerancePct,

        @JsonProperty("amount_tolerance_abs")
        @ValidMoney
        String amountToleranceAbs,

        @JsonProperty("require_grn")
        Boolean requireGrn,

        @JsonProperty("is_default")
        Boolean isDefault,

        @JsonProperty("is_active")
        Boolean active
) {
}