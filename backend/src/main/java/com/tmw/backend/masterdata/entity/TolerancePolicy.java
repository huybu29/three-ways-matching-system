package com.tmw.backend.masterdata.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "tolerance_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TolerancePolicy extends BaseEntity {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "item_category", length = 100)
    private String itemCategory;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "qty_tolerance_pct", precision = 19, scale = 4, nullable = false)
    private BigDecimal qtyTolerancePct;

    @Column(name = "price_tolerance_pct", precision = 19, scale = 4, nullable = false)
    private BigDecimal priceTolerancePct;

    @Column(name = "amount_tolerance_abs", precision = 19, scale = 4, nullable = false)
    private BigDecimal amountToleranceAbs;

    @Column(name = "require_grn", nullable = false)
    private boolean requireGrn = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private Integer version;

    public TolerancePolicy(
            String name,
            Supplier supplier,
            String itemCategory,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal qtyTolerancePct,
            BigDecimal priceTolerancePct,
            BigDecimal amountToleranceAbs,
            boolean requireGrn,
            boolean isDefault
    ) {
        applyAndValidate(name, supplier, itemCategory, minAmount, maxAmount,
                qtyTolerancePct, priceTolerancePct, amountToleranceAbs, requireGrn, isDefault);
        this.active = true;
    }

    public TolerancePolicy(
            Supplier supplier,
            String currency,
            BigDecimal priceTolerancePct,
            BigDecimal priceToleranceAmount,
            BigDecimal qtyTolerancePct,
            BigDecimal qtyToleranceAmount,
            boolean requireGrn
    ) {
        this(
                "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                supplier,
                null,
                null,
                null,
                qtyTolerancePct != null ? qtyTolerancePct : BigDecimal.ZERO,
                priceTolerancePct != null ? priceTolerancePct : BigDecimal.ZERO,
                priceToleranceAmount != null ? priceToleranceAmount : BigDecimal.ZERO,
                requireGrn,
                supplier == null
        );
    }

    public boolean isActive() {
        return this.active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void update(
            String name,
            Supplier supplier,
            String itemCategory,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal qtyTolerancePct,
            BigDecimal priceTolerancePct,
            BigDecimal amountToleranceAbs,
            boolean requireGrn,
            boolean isDefault
    ) {
        applyAndValidate(name, supplier, itemCategory, minAmount, maxAmount,
                qtyTolerancePct, priceTolerancePct, amountToleranceAbs, requireGrn, isDefault);
    }

    private void applyAndValidate(
            String name,
            Supplier supplier,
            String itemCategory,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal qtyTolerancePct,
            BigDecimal priceTolerancePct,
            BigDecimal amountToleranceAbs,
            boolean requireGrn,
            boolean isDefault
    ) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên chính sách dung sai không được để trống");
        }

        String normalizedCategory = (itemCategory == null || itemCategory.isBlank()) ? null : itemCategory.trim();
        BigDecimal scaledMin = scaleOrNull(minAmount);
        BigDecimal scaledMax = scaleOrNull(maxAmount);
        BigDecimal scaledQtyPct = requireNonNullScaled(qtyTolerancePct, "qty_tolerance_pct");
        BigDecimal scaledPricePct = requireNonNullScaled(priceTolerancePct, "price_tolerance_pct");
        BigDecimal scaledAmountAbs = requireNonNullScaled(amountToleranceAbs, "amount_tolerance_abs");

        validatePercentage(scaledQtyPct, "qty_tolerance_pct");
        validatePercentage(scaledPricePct, "price_tolerance_pct");

        if (scaledAmountAbs.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amount_tolerance_abs không được âm");
        }
        if (scaledMin != null && scaledMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "min_amount không được âm");
        }
        if (scaledMax != null && scaledMax.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "max_amount không được âm");
        }
        if (scaledMin != null && scaledMax != null && scaledMin.compareTo(scaledMax) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "min_amount phải nhỏ hơn hoặc bằng max_amount");
        }
        if (isDefault && (supplier != null || normalizedCategory != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Chính sách mặc định (is_default) không được gắn cố định nhà cung cấp hoặc nhóm hàng");
        }

        this.name = name.trim();
        this.supplier = supplier;
        this.itemCategory = normalizedCategory;
        this.minAmount = scaledMin;
        this.maxAmount = scaledMax;
        this.qtyTolerancePct = scaledQtyPct;
        this.priceTolerancePct = scaledPricePct;
        this.amountToleranceAbs = scaledAmountAbs;
        this.requireGrn = requireGrn;
        this.isDefault = isDefault;
    }

    private static BigDecimal scaleOrNull(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal requireNonNullScaled(BigDecimal value, String field) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " không được để trống");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static void validatePercentage(BigDecimal pct, String field) {
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(HUNDRED) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " phải nằm trong khoảng từ 0 đến 100");
        }
    }
}