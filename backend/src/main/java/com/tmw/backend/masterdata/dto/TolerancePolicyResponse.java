package com.tmw.backend.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TolerancePolicyResponse(
        UUID id,

        String name,

        @JsonProperty("supplier_id")
        UUID supplierId,

        @JsonProperty("supplier_code")
        String supplierCode,

        @JsonProperty("item_category")
        String itemCategory,

        @JsonProperty("min_amount")
        String minAmount,

        @JsonProperty("max_amount")
        String maxAmount,

        @JsonProperty("qty_tolerance_pct")
        String qtyTolerancePct,

        @JsonProperty("price_tolerance_pct")
        String priceTolerancePct,

        @JsonProperty("amount_tolerance_abs")
        String amountToleranceAbs,

        @JsonProperty("require_grn")
        boolean requireGrn,

        @JsonProperty("is_default")
        boolean isDefault,

        @JsonProperty("is_active")
        boolean isActive,

        Integer version,

        @JsonProperty("created_at")
        Instant createdAt
) {
    public static TolerancePolicyResponse from(TolerancePolicy policy) {
        Supplier supplier = policy.getSupplier();
        return new TolerancePolicyResponse(
                policy.getId(),
                policy.getName(),
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getCode() : null,
                policy.getItemCategory(),
                toPlainStringOrNull(policy.getMinAmount()),
                toPlainStringOrNull(policy.getMaxAmount()),
                toPlainStringOrNull(policy.getQtyTolerancePct()),
                toPlainStringOrNull(policy.getPriceTolerancePct()),
                toPlainStringOrNull(policy.getAmountToleranceAbs()),
                policy.isRequireGrn(),
                policy.isDefault(),
                policy.isActive(),
                policy.getVersion(),
                policy.getCreatedAt()
        );
    }

    private static String toPlainStringOrNull(BigDecimal value) {
        return value != null ? value.toPlainString() : null;
    }
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
}