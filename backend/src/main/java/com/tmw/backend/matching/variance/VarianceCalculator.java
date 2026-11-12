package com.tmw.backend.matching.variance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

import static com.tmw.backend.matching.entity.DecimalScales.MONEY_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.PCT_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.QTY_SCALE;
import static com.tmw.backend.matching.entity.DecimalScales.ROUNDING;

/**
 * Khung thuật toán tính độ lệch số lượng (qty_variance) và độ lệch giá (price_variance).
 *
 * <p>Thuần tính toán, không truy cập DB, không có state → dễ unit test và dùng lại được ở
 * Auto-Match (GĐ3), Manual match (GĐ3) và Rematch (GĐ4).</p>
 *
 * <p>Việc so sánh với dung sai (qty_tolerance_pct, price_tolerance_pct, amount_tolerance_abs)
 * KHÔNG nằm ở đây – sẽ ghép với ToleranceEvaluationService của BE-2 ở Giai đoạn 3.</p>
 */
@Component
public class VarianceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public VarianceResult calculate(VarianceInput in) {
        Objects.requireNonNull(in, "input");

        BigDecimal referenceQty = qty(in.referenceQty());
        BigDecimal invoiceQty = qty(in.invoiceQty());
        BigDecimal invoicePrice = money(in.invoiceUnitPrice());
        BigDecimal poPrice = money(in.poUnitPrice());

        // --- Độ lệch số lượng ---
        BigDecimal qtyVariance = qty(invoiceQty.subtract(referenceQty));
        BigDecimal qtyVariancePct = percent(qtyVariance, referenceQty);

        // --- Độ lệch đơn giá ---
        BigDecimal priceVariance = money(invoicePrice.subtract(poPrice));
        BigDecimal priceVariancePct = percent(priceVariance, poPrice);

        // --- Độ lệch thành tiền (phục vụ amount_tolerance_abs) ---
        BigDecimal invoiceAmount = in.invoiceAmount() != null
                ? money(in.invoiceAmount())
                : money(invoiceQty.multiply(invoicePrice));
        BigDecimal expectedAmount = money(referenceQty.multiply(poPrice));
        BigDecimal amountVariance = money(invoiceAmount.subtract(expectedAmount));

        return new VarianceResult(
                referenceQty,
                qtyVariance, qtyVariancePct,
                priceVariance, priceVariancePct,
                invoiceAmount, expectedAmount, amountVariance);
    }

    /**
     * Cộng dồn số lượng nhận từ nhiều GRN line (giao hàng từng phần) cho 1 dòng PO.
     * Bỏ qua phần tử null.
     */
    public static BigDecimal sumQty(Collection<BigDecimal> quantities) {
        if (quantities == null) return qty(BigDecimal.ZERO);
        return qty(quantities.stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ------------------------------------------------------------------ helpers

    /** variance / base × 100, null nếu base = 0 (không chia được). */
    static BigDecimal percent(BigDecimal variance, BigDecimal base) {
        if (base.signum() == 0) {
            return variance.signum() == 0 ? BigDecimal.ZERO.setScale(PCT_SCALE) : null;
        }
        // chia với độ chính xác dư rồi mới làm tròn về PCT_SCALE để tránh sai số tích lũy
        return variance.multiply(HUNDRED)
                .divide(base, PCT_SCALE + 6, ROUNDING)
                .setScale(PCT_SCALE, ROUNDING);
    }

    static BigDecimal qty(BigDecimal v)   { return v.setScale(QTY_SCALE, ROUNDING); }
    static BigDecimal money(BigDecimal v) { return v.setScale(MONEY_SCALE, ROUNDING); }
}
