package com.tmw.backend.matching.variance;

import java.math.BigDecimal;

/**
 * Kết quả tính chênh lệch của một cặp ghép.
 *
 * @param qtyVariancePct null khi referenceQty = 0 (chưa nhận hàng / không còn số lượng tham chiếu):
 *                       tỷ lệ % không xác định, xem {@link #missingReferenceQty()}
 */
public record VarianceResult(
        BigDecimal referenceQty,
        BigDecimal qtyVariance,
        BigDecimal qtyVariancePct,
        BigDecimal priceVariance,
        BigDecimal priceVariancePct,
        BigDecimal amountVariance
) {

    /**
     * true khi không có số lượng tham chiếu (referenceQty = 0). Đây là tình huống nghiệp vụ bình thường,
     * không phải lỗi đầu vào: engine phải tạo MatchException loại MISSING_GRN (3-way) cho dòng này
     * rồi xử lý tiếp các dòng còn lại, thay vì làm hỏng cả lượt chạy.
     */
    public boolean missingReferenceQty() {
        return referenceQty != null && referenceQty.signum() == 0;
    }
}
