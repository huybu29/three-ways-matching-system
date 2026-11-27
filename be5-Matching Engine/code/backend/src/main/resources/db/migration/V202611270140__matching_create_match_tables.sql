-- =============================================================================
-- BE-5 · Matching · Tạo bảng match và match_line
-- Khớp 1-1 với entity com.tmw.backend.matching.entity.Match / MatchLine
-- (chạy được với spring.jpa.hibernate.ddl-auto=validate).
--
-- Thay thế file V1_0_5__create_match_tables.sql cũ (tên bảng/cột không khớp entity).
--
-- Chưa khai báo FK sang invoice / invoice_line / purchase_order / po_line /
-- goods_receipt_line / tolerance_policy vì các bảng đó chưa có migration tạo bảng trên develop.
-- Sẽ bổ sung bằng một migration riêng khi BE-2/BE-3/BE-4 đã có migration của họ.
-- =============================================================================

CREATE TABLE match (
    id                      UUID          PRIMARY KEY,
    invoice_id              UUID          NOT NULL,
    status                  VARCHAR(30)   NOT NULL,
    source                  VARCHAR(20)   NOT NULL,
    allocation_state        VARCHAR(20)   NOT NULL,
    overall_confidence      NUMERIC(5,4),
    tolerance_policy_id     UUID,
    has_variance_exception  BOOLEAN       NOT NULL DEFAULT FALSE,
    summary_reason          VARCHAR(500),
    proposed_by             UUID          NOT NULL,
    approved_by             UUID,
    approved_at             TIMESTAMPTZ,
    posted_by               UUID,
    posted_at               TIMESTAMPTZ,
    reject_reason           VARCHAR(500),
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_match_status
        CHECK (status IN ('PROPOSED', 'APPROVED', 'POSTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_match_source
        CHECK (source IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_match_allocation_state
        CHECK (allocation_state IN ('NONE', 'RESERVED', 'ALLOCATED', 'RELEASED')),
    -- AUTO bắt buộc có điểm 0..1, MANUAL không có điểm
    CONSTRAINT chk_match_confidence_by_source CHECK (
        (source = 'AUTO'   AND overall_confidence IS NOT NULL AND overall_confidence BETWEEN 0 AND 1)
     OR (source = 'MANUAL' AND overall_confidence IS NULL)
    ),
    -- Nhất quán vòng đời: đã duyệt/ghi sổ thì phải có người và thời điểm; reject/cancel phải có lý do
    CONSTRAINT chk_match_approved_audit CHECK (
        status NOT IN ('APPROVED', 'POSTED') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT chk_match_posted_audit CHECK (
        status <> 'POSTED' OR (posted_by IS NOT NULL AND posted_at IS NOT NULL)
    ),
    CONSTRAINT chk_match_reject_reason CHECK (
        status NOT IN ('REJECTED', 'CANCELLED') OR reject_reason IS NOT NULL
    )
);

-- Mỗi hóa đơn chỉ có tối đa 1 Match đang hoạt động (PROPOSED/APPROVED).
-- Match REJECTED/CANCELLED/POSTED không bị giới hạn → hóa đơn được đối chiếu lại sau khi bị từ chối/hủy.
CREATE UNIQUE INDEX uk_match_active_invoice
    ON match (invoice_id)
    WHERE status IN ('PROPOSED', 'APPROVED');

-- Lịch sử Match theo hóa đơn (findByInvoiceIdOrderByCreatedAtDesc)
CREATE INDEX idx_match_invoice_created ON match (invoice_id, created_at DESC);
-- GET /matches lọc theo status, phân trang cursor (created_at, id)
CREATE INDEX idx_match_status_created ON match (status, created_at DESC, id);


CREATE TABLE match_line (
    id                   UUID           PRIMARY KEY,
    match_id             UUID           NOT NULL,
    line_no              INTEGER        NOT NULL,
    invoice_line_id      UUID           NOT NULL,
    po_id                UUID           NOT NULL,
    po_line_id           UUID           NOT NULL,
    grn_line_id          UUID,
    match_type           VARCHAR(16)    NOT NULL,
    matched_qty          NUMERIC(19,4)  NOT NULL,
    invoice_unit_price   NUMERIC(19,4)  NOT NULL,
    po_unit_price        NUMERIC(19,4)  NOT NULL,
    matched_amount       NUMERIC(19,4)  NOT NULL,
    reference_qty        NUMERIC(19,4),
    qty_variance         NUMERIC(19,4),
    qty_variance_pct     NUMERIC(19,4),
    price_variance       NUMERIC(19,4),
    price_variance_pct   NUMERIC(19,4),
    amount_variance      NUMERIC(19,4),
    within_tolerance     BOOLEAN,
    confidence_score     NUMERIC(5,4),
    allocation_state     VARCHAR(16)    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_match_line_match
        FOREIGN KEY (match_id) REFERENCES match (id) ON DELETE CASCADE,
    CONSTRAINT uk_match_line_match_inv_line_po_line
        UNIQUE (match_id, invoice_line_id, po_line_id),
    CONSTRAINT uk_match_line_match_line_no
        UNIQUE (match_id, line_no),
    CONSTRAINT chk_match_line_match_type
        CHECK (match_type IN ('TWO_WAY', 'THREE_WAY')),
    CONSTRAINT chk_match_line_grn_for_three_way
        CHECK (match_type = 'TWO_WAY' OR grn_line_id IS NOT NULL),
    CONSTRAINT chk_match_line_qty_positive
        CHECK (matched_qty > 0),
    CONSTRAINT chk_match_line_price_non_negative
        CHECK (invoice_unit_price >= 0 AND po_unit_price >= 0),
    CONSTRAINT chk_match_line_confidence_range
        CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1),
    CONSTRAINT chk_match_line_allocation_state
        CHECK (allocation_state IN ('NONE', 'RESERVED', 'ALLOCATED', 'RELEASED'))
);

-- Tổng reserved/allocated theo dòng PO (MatchLineRepository.sumMatchedQty, đối soát với BE-3)
CREATE INDEX idx_match_line_po_line_state ON match_line (po_line_id, allocation_state);
-- Số lượng đã ghép theo dòng hóa đơn (sumActiveQtyByInvoiceLine)
CREATE INDEX idx_match_line_invoice_line ON match_line (invoice_line_id);
CREATE INDEX idx_match_line_grn_line ON match_line (grn_line_id) WHERE grn_line_id IS NOT NULL;
