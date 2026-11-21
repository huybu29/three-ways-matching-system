package com.tmw.backend.masterdata.service;

import java.util.UUID;

/**
 * Kết quả đánh giá dung sai.
 * priorityLevel: 1 = NCC + nhóm hàng, 2 = NCC, 3 = nhóm hàng hoặc khoảng tiền, 4 = mặc định.
 */
public record ToleranceEvaluation(
        UUID policyId,
        String policyName,
        int priorityLevel,
        boolean qtyWithinTolerance,
        boolean priceWithinTolerance,
        boolean amountWithinTolerance,
        boolean grnRequirementMet,
        boolean withinTolerance
) {
}
