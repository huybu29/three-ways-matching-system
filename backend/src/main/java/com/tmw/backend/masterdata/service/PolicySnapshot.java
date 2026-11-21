package com.tmw.backend.masterdata.service;

import com.tmw.backend.masterdata.entity.TolerancePolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bản chụp bất biến của TolerancePolicy để đưa vào cache (không giữ entity/lazy proxy). */
public record PolicySnapshot(
        UUID id,
        String name,
        UUID supplierId,
        String itemCategory,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal qtyTolerancePct,
        BigDecimal priceTolerancePct,
        BigDecimal amountToleranceAbs,
        boolean requireGrn,
        boolean defaultPolicy,
        Instant createdAt
) {
    public static PolicySnapshot from(TolerancePolicy p) {
        return new PolicySnapshot(
                p.getId(),
                p.getName(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getItemCategory(),
                p.getMinAmount(),
                p.getMaxAmount(),
                p.getQtyTolerancePct(),
                p.getPriceTolerancePct(),
                p.getAmountToleranceAbs(),
                p.isRequireGrn(),
                p.isDefault(),
                p.getCreatedAt()
        );
    }
}
