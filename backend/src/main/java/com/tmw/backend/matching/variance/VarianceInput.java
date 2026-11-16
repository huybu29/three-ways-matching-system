package com.tmw.backend.matching.variance;

import com.tmw.backend.matching.entity.MatchType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Dữ liệu đầu vào để tính độ lệch cho MỘT cặp ghép (dòng hóa đơn ↔ dòng PO [↔ GRN]).
 *
 * @param matchType         TWO_WAY hoặc THREE_WAY
 * @param invoiceQty        số lượng trên hóa đơn được ghép vào dòng PO này (> 0)
 * @param invoiceUnitPrice  đơn giá trên hóa đơn (chưa VAT, >= 0)
 * @param invoiceAmount     thành tiền dòng hóa đơn chưa VAT; null → tự tính = invoiceQty × invoiceUnitPrice
 * @param poUnitPrice       đơn giá trên PO (chưa VAT, >= 0)
 * @param orderedQty        số lượng còn được phép xuất hóa đơn trên dòng PO (dùng cho TWO_WAY)
 * @param receivedQty       tổng số lượng đã nhận kho (cộng dồn các GRN line liên quan) – bắt buộc với THREE_WAY
 */
public record VarianceInput(
        MatchType matchType,
        BigDecimal invoiceQty,
        BigDecimal invoiceUnitPrice,
        BigDecimal invoiceAmount,
        BigDecimal poUnitPrice,
        BigDecimal orderedQty,
        BigDecimal receivedQty
) {

    public VarianceInput {
        Objects.requireNonNull(matchType, "matchType");
        requirePositive(invoiceQty, "invoiceQty");
        requireNonNegative(invoiceUnitPrice, "invoiceUnitPrice");
        requireNonNegative(poUnitPrice, "poUnitPrice");
        if (invoiceAmount != null) requireNonNegative(invoiceAmount, "invoiceAmount");

        if (matchType == MatchType.THREE_WAY) {
            requireNonNegative(receivedQty, "receivedQty (required for THREE_WAY)");
        } else {
            requireNonNegative(orderedQty, "orderedQty (required for TWO_WAY)");
        }
    }

    /** Builder tiện cho 3-way. */
    public static VarianceInput threeWay(BigDecimal invoiceQty, BigDecimal invoiceUnitPrice,
                                         BigDecimal poUnitPrice, BigDecimal receivedQty) {
        return new VarianceInput(MatchType.THREE_WAY, invoiceQty, invoiceUnitPrice, null,
                poUnitPrice, null, receivedQty);
    }

    /** Builder tiện cho 2-way. */
    public static VarianceInput twoWay(BigDecimal invoiceQty, BigDecimal invoiceUnitPrice,
                                       BigDecimal poUnitPrice, BigDecimal orderedQty) {
        return new VarianceInput(MatchType.TWO_WAY, invoiceQty, invoiceUnitPrice, null,
                poUnitPrice, orderedQty, null);
    }

    /** Số lượng tham chiếu để so sánh: GRN (3-way) hoặc PO (2-way). */
    public BigDecimal referenceQty() {
        return matchType == MatchType.THREE_WAY ? receivedQty : orderedQty;
    }

    private static void requirePositive(BigDecimal v, String name) {
        if (v == null || v.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + v);
        }
    }

    private static void requireNonNegative(BigDecimal v, String name) {
        if (v == null || v.signum() < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + v);
        }
    }
}
