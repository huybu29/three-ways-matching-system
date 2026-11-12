package com.tmw.backend.matching.variance;

import java.math.BigDecimal;

/**
 * Kết quả tính độ lệch của một cặp ghép.
 * Quy ước dấu: dương = hóa đơn CAO hơn tham chiếu (bất lợi cho bên mua), âm = thấp hơn.
 *
 * @param referenceQty      số lượng tham chiếu (GRN hoặc PO)
 * @param qtyVariance       invoiceQty − referenceQty
 * @param qtyVariancePct    qtyVariance / referenceQty × 100; null nếu referenceQty = 0
 * @param priceVariance     invoiceUnitPrice − poUnitPrice
 * @param priceVariancePct  priceVariance / poUnitPrice × 100; null nếu poUnitPrice = 0
 * @param invoiceAmount     thành tiền hóa đơn (chưa VAT)
 * @param expectedAmount    referenceQty × poUnitPrice
 * @param amountVariance    invoiceAmount − expectedAmount
 */
public record VarianceResult(
        BigDecimal referenceQty,
        BigDecimal qtyVariance,
        BigDecimal qtyVariancePct,
        BigDecimal priceVariance,
        BigDecimal priceVariancePct,
        BigDecimal invoiceAmount,
        BigDecimal expectedAmount,
        BigDecimal amountVariance
) {

    public boolean hasQtyVariance()    { return qtyVariance.signum() != 0; }
    public boolean hasPriceVariance()  { return priceVariance.signum() != 0; }
    public boolean hasAmountVariance() { return amountVariance.signum() != 0; }

    /** Khớp tuyệt đối (không cần đến dung sai). */
    public boolean isExactMatch() {
        return !hasQtyVariance() && !hasPriceVariance() && !hasAmountVariance();
    }

    /**
     * Lệch % không xác định được (tham chiếu = 0 nhưng có lệch),
     * vd. xuất hóa đơn khi chưa nhận kho → luôn coi là vượt dung sai ở Giai đoạn 3.
     */
    public boolean qtyVarianceUnbounded()   { return qtyVariancePct == null && hasQtyVariance(); }
    public boolean priceVarianceUnbounded() { return priceVariancePct == null && hasPriceVariance(); }
}
