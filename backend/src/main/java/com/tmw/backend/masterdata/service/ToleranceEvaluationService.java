package com.tmw.backend.masterdata.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Chọn chính sách dung sai theo thứ tự ưu tiên và đánh giá chênh lệch.
 * Ưu tiên: (1) NCC + nhóm hàng -> (2) NCC -> (3) nhóm hàng hoặc khoảng tiền -> (4) mặc định.
 * Chỉ nhận số thuần (BigDecimal) để không phụ thuộc module matching.
 */
@Service
public class ToleranceEvaluationService {

    private final ActivePolicyProvider provider;

    public ToleranceEvaluationService(ActivePolicyProvider provider) {
        this.provider = provider;
    }

    /**
     * @param qtyVariancePct   % lệch số lượng (giá trị tuyệt đối)
     * @param priceVariancePct % lệch đơn giá (giá trị tuyệt đối)
     * @param amountVariance   chênh lệch thành tiền (có thể âm, sẽ lấy trị tuyệt đối)
     * @param grnPresent       đã có phiếu nhập kho (GRN) hay chưa
     */
    public ToleranceEvaluation evaluate(
            UUID supplierId,
            String itemCategory,
            BigDecimal documentAmount,
            BigDecimal qtyVariancePct,
            BigDecimal priceVariancePct,
            BigDecimal amountVariance,
            boolean grnPresent
    ) {
        if (documentAmount == null || qtyVariancePct == null || priceVariancePct == null || amountVariance == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dữ liệu đánh giá dung sai không được để trống");
        }
        PolicySnapshot policy = selectPolicy(provider.activePolicies(), supplierId, itemCategory, documentAmount);

        boolean qtyOk = qtyVariancePct.abs().compareTo(policy.qtyTolerancePct()) <= 0;
        boolean priceOk = priceVariancePct.abs().compareTo(policy.priceTolerancePct()) <= 0;
        boolean amountOk = amountVariance.abs().compareTo(policy.amountToleranceAbs()) <= 0;
        boolean grnOk = !policy.requireGrn() || grnPresent;

        return new ToleranceEvaluation(
                policy.id(), policy.name(), rank(policy),
                qtyOk, priceOk, amountOk, grnOk,
                qtyOk && priceOk && amountOk && grnOk
        );
    }

    /** Chọn chính sách phù hợp nhất. Không có chính sách nào phù hợp -> lỗi. */
    static PolicySnapshot selectPolicy(List<PolicySnapshot> policies, UUID supplierId,
                                       String itemCategory, BigDecimal amount) {
        return policies.stream()
                .filter(p -> p.supplierId() == null || p.supplierId().equals(supplierId))
                .filter(p -> p.itemCategory() == null
                        || (itemCategory != null && p.itemCategory().equalsIgnoreCase(itemCategory.trim())))
                .filter(p -> p.minAmount() == null || p.minAmount().compareTo(amount) <= 0)
                .filter(p -> p.maxAmount() == null || p.maxAmount().compareTo(amount) >= 0)
                .min(Comparator
                        .comparingInt(ToleranceEvaluationService::rank)
                        // cùng mức: chính sách có nhóm hàng trước khoảng tiền; mặc định trước chính sách chung khác
                        .thenComparing(p -> p.itemCategory() == null)
                        .thenComparing(p -> !p.defaultPolicy())
                        .thenComparing(PolicySnapshot::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PolicySnapshot::id))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy chính sách dung sai phù hợp (kể cả chính sách mặc định)"));
    }

    static int rank(PolicySnapshot p) {
        boolean hasSupplier = p.supplierId() != null;
        boolean hasCategory = p.itemCategory() != null;
        boolean hasRange = p.minAmount() != null || p.maxAmount() != null;
        if (hasSupplier && hasCategory) return 1;
        if (hasSupplier) return 2;
        if (hasCategory || hasRange) return 3;
        return 4;
    }
}
