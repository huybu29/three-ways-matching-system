package com.tmw.backend.purchaseorder.api;

import java.util.List;

/**
 * Truy vấn chỉ-đọc PO và số dư hạn mức cho engine ghép (mục 1.3 bản phân công v2).
 *
 * <p>Chủ sở hữu: BE-3. Chữ ký và bản cài đặt JDBC đầu tiên do BE-5 đề xuất ở giai đoạn 2
 * để CandidateFinder không bị chặn; BE-3 review và tiếp quản.</p>
 */
public interface PurchaseOrderQueryApi {

    /**
     * Tìm các dòng PO ứng viên của một nhà cung cấp cho việc ghép hóa đơn.
     *
     * <p>Chỉ trả dòng thuộc PO đang mở (OPEN, PARTIALLY_INVOICED), đặt trước hoặc trong ngày hóa đơn,
     * cùng loại tiền, và còn hạn mức PO (ordered - allocated - reserved &gt; 0). Không lọc theo vế GRN:
     * dòng chưa nhận hàng vẫn được trả để engine sinh MISSING_GRN.</p>
     *
     * <p>Thứ tự: order_date tăng dần (PO cũ trước), rồi po_number, line_no.</p>
     */
    List<PoLineCandidateView> findCandidateLines(PoLineCandidateQuery query);
}
