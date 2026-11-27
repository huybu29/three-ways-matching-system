package com.tmw.backend.purchaseorder.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Điều kiện tìm dòng PO ứng viên.
 *
 * <p>Một dòng PO được lấy nếu thỏa điều kiện chung (nhà cung cấp, trạng thái, ngày, tiền tệ, còn hạn mức)
 * VÀ ít nhất một trong các điều kiện: trùng mã hàng, là dòng được gợi ý, thuộc PO có số trùng
 * {@code poNumber}, hoặc thuộc PO {@code purchaseOrderId}.</p>
 *
 * @param supplierId      bắt buộc
 * @param orderDateTo     bắt buộc: chỉ lấy PO có order_date &lt;= ngày này (thường là issue_date của hóa đơn)
 * @param currency        mã tiền tệ (vd "VND"); null = không lọc
 * @param itemCodes       mã hàng trên hóa đơn (tự chuẩn hóa trim + chữ hoa như po_line.item_code)
 * @param poLineIds       dòng PO được gợi ý trực tiếp trên dòng hóa đơn (invoice_line.po_line_id)
 * @param poNumber        số PO in trên hóa đơn (chuỗi); null nếu không có
 * @param purchaseOrderId PO được gợi ý ở header hóa đơn (invoice.purchase_order_id); null nếu không có
 * @param limit           số dòng tối đa trả về (1..1000)
 */
public record PoLineCandidateQuery(
        UUID supplierId,
        LocalDate orderDateTo,
        String currency,
        Set<String> itemCodes,
        Set<UUID> poLineIds,
        String poNumber,
        UUID purchaseOrderId,
        int limit
) {
    public static final int MAX_LIMIT = 1000;

    public PoLineCandidateQuery {
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "supplierId không được để trống");
        }
        if (orderDateTo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderDateTo không được để trống");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "limit phải nằm trong khoảng 1.." + MAX_LIMIT);
        }
        currency = blankToNull(currency) == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        itemCodes = normalizeCodes(itemCodes);
        poLineIds = poLineIds == null ? Set.of()
                : poLineIds.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        poNumber = blankToNull(poNumber) == null ? null : poNumber.trim().toUpperCase(Locale.ROOT);
    }

    /** Không có tiêu chí "hoặc" nào → chắc chắn không có ứng viên, khỏi truy vấn DB. */
    public boolean hasNoSelector() {
        return itemCodes.isEmpty() && poLineIds.isEmpty() && poNumber == null && purchaseOrderId == null;
    }

    public static String normalizeItemCode(String code) {
        String trimmed = blankToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static Set<String> normalizeCodes(Collection<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream()
                .map(PoLineCandidateQuery::normalizeItemCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
