-- =============================================================================
-- BE-5 · Matching · Index hỗ trợ CandidateFinder (PurchaseOrderQueryApi.findCandidateLines)
--
-- Repeatable migration (R__): Flyway luôn chạy file này SAU mọi migration V__, nên các bảng
-- purchase_order / po_line / po_line_capacity của BE-3 chắc chắn đã tồn tại, dù họ đặt
-- timestamp migration sau BE-5. Sửa file này thì Flyway tự chạy lại (IF NOT EXISTS → an toàn).
--
-- Đo trên PostgreSQL 16 với 1.000 PO / 5.000 dòng (xem docs/be5/candidate-finder-performance.md).
-- =============================================================================

-- Lọc PO theo nhà cung cấp + trạng thái mở + ngày đặt; INCLUDE để đọc po_number/currency không cần heap.
CREATE INDEX IF NOT EXISTS idx_purchase_order_supplier_status_date
    ON purchase_order (supplier_id, status, order_date)
    INCLUDE (po_number, currency);

-- Nối PO → dòng và lọc mã hàng trong cùng một index.
CREATE INDEX IF NOT EXISTS idx_po_line_po_item
    ON po_line (purchase_order_id, item_code);

-- Tra dòng PO theo mã hàng (dùng cho các truy vấn theo item_code không qua PO).
CREATE INDEX IF NOT EXISTS idx_po_line_item_code
    ON po_line (item_code);
