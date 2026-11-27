-- =============================================================================
-- Migration V1_0_5: Create match_proposals & match_lines tables
-- Role: BE-5 (Matching Engine) – Giai đoạn 1
-- Cột id / version / created_at khớp với BaseEntity.
-- FK sang bảng của BE-2/BE-3/BE-4 để dạng ALTER ở cuối – bật khi các nhánh đó đã merge.
-- =============================================================================

CREATE TABLE IF NOT EXISTS match_proposals (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id            UUID           NOT NULL,
    supplier_id           UUID           NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    source                VARCHAR(16)    NOT NULL,
    confidence            NUMERIC(5,4),
    total_matched_amount  NUMERIC(19,4)  NOT NULL DEFAULT 0,

    proposed_by           UUID           NOT NULL,
    proposed_at           TIMESTAMPTZ    NOT NULL,
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    posted_by             UUID,
    posted_at             TIMESTAMPTZ,
    rejected_by           UUID,
    rejected_at           TIMESTAMPTZ,
    cancelled_by          UUID,
    cancelled_at          TIMESTAMPTZ,
    reason                VARCHAR(1000),

    version               INTEGER        NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_mp_status CHECK (status IN ('PROPOSED','APPROVED','POSTED','REJECTED','CANCELLED')),
    CONSTRAINT ck_mp_source CHECK (source IN ('AUTO','MANUAL')),
    CONSTRAINT ck_mp_confidence CHECK (
        (source = 'AUTO'   AND confidence IS NOT NULL AND confidence BETWEEN 0 AND 1) OR
        (source = 'MANUAL' AND confidence IS NULL)
    ),
    -- Ràng buộc nhất quán vòng đời ở tầng DB (phòng thủ thêm ngoài code)
    CONSTRAINT ck_mp_approved CHECK (status NOT IN ('APPROVED','POSTED') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_mp_posted   CHECK (status <> 'POSTED'    OR (posted_by IS NOT NULL AND posted_at IS NOT NULL)),
    CONSTRAINT ck_mp_rejected CHECK (status <> 'REJECTED'  OR (rejected_by IS NOT NULL AND reason IS NOT NULL)),
    CONSTRAINT ck_mp_cancel   CHECK (status <> 'CANCELLED' OR cancelled_by IS NOT NULL),
    -- SoD ở tầng DB: người duyệt/ghi sổ không trùng người trước
    CONSTRAINT ck_mp_sod_approve CHECK (approved_by IS NULL OR approved_by <> proposed_by),
    CONSTRAINT ck_mp_sod_post    CHECK (posted_by   IS NULL OR (posted_by <> proposed_by AND posted_by <> approved_by))
);

CREATE INDEX IF NOT EXISTS ix_mp_invoice        ON match_proposals (invoice_id);
CREATE INDEX IF NOT EXISTS ix_mp_status_created ON match_proposals (status, proposed_at DESC, id);
CREATE INDEX IF NOT EXISTS ix_mp_supplier       ON match_proposals (supplier_id);

-- Mỗi hóa đơn chỉ có tối đa 1 đề xuất đang hiệu lực
CREATE UNIQUE INDEX IF NOT EXISTS ux_mp_invoice_active
    ON match_proposals (invoice_id)
    WHERE status IN ('PROPOSED','APPROVED');


CREATE TABLE IF NOT EXISTS match_lines (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    match_proposal_id   UUID           NOT NULL REFERENCES match_proposals(id) ON DELETE CASCADE,
    line_no             INT            NOT NULL,

    invoice_line_id     UUID           NOT NULL,
    po_id               UUID           NOT NULL,
    po_line_id          UUID           NOT NULL,
    grn_line_id         UUID,
    match_type          VARCHAR(16)    NOT NULL,

    matched_qty         NUMERIC(19,4)  NOT NULL,
    invoice_unit_price  NUMERIC(19,4)  NOT NULL,
    po_unit_price       NUMERIC(19,4)  NOT NULL,
    matched_amount      NUMERIC(19,4)  NOT NULL,

    reference_qty       NUMERIC(19,4),
    qty_variance        NUMERIC(19,4),
    qty_variance_pct    NUMERIC(9,4),
    price_variance      NUMERIC(19,4),
    price_variance_pct  NUMERIC(9,4),
    amount_variance     NUMERIC(19,4),
    within_tolerance    BOOLEAN,

    status              VARCHAR(16)    NOT NULL DEFAULT 'RESERVED',
    version             INTEGER        NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ml_line_no      UNIQUE (match_proposal_id, line_no),
    CONSTRAINT ck_ml_match_type   CHECK (match_type IN ('TWO_WAY','THREE_WAY')),
    CONSTRAINT ck_ml_grn_required CHECK (match_type = 'TWO_WAY' OR grn_line_id IS NOT NULL),
    CONSTRAINT ck_ml_qty_pos      CHECK (matched_qty > 0),
    CONSTRAINT ck_ml_price_nonneg CHECK (invoice_unit_price >= 0 AND po_unit_price >= 0),
    CONSTRAINT ck_ml_status       CHECK (status IN ('RESERVED','ALLOCATED','RELEASED'))
);

CREATE INDEX IF NOT EXISTS ix_ml_proposal     ON match_lines (match_proposal_id);
CREATE INDEX IF NOT EXISTS ix_ml_invoice_line ON match_lines (invoice_line_id);
CREATE INDEX IF NOT EXISTS ix_ml_grn_line     ON match_lines (grn_line_id) WHERE grn_line_id IS NOT NULL;
-- Phục vụ tính reserved/allocated theo dòng PO (BE-3)
CREATE INDEX IF NOT EXISTS ix_ml_po_line_status ON match_lines (po_line_id, status);


-- ---------------------------------------------------------------------
-- FK sang bảng chứng từ – bỏ comment khi nhánh BE-2/BE-3/BE-4 đã merge vào main
-- ---------------------------------------------------------------------
-- ALTER TABLE match_proposals ADD CONSTRAINT fk_mp_invoice  FOREIGN KEY (invoice_id)      REFERENCES invoices(id);
-- ALTER TABLE match_proposals ADD CONSTRAINT fk_mp_supplier FOREIGN KEY (supplier_id)     REFERENCES supplier(id);
-- ALTER TABLE match_lines     ADD CONSTRAINT fk_ml_inv_line FOREIGN KEY (invoice_line_id) REFERENCES invoice_lines(id);
-- ALTER TABLE match_lines     ADD CONSTRAINT fk_ml_po       FOREIGN KEY (po_id)           REFERENCES purchase_orders(id);
-- ALTER TABLE match_lines     ADD CONSTRAINT fk_ml_po_line  FOREIGN KEY (po_line_id)      REFERENCES purchase_order_lines(id);
-- ALTER TABLE match_lines     ADD CONSTRAINT fk_ml_grn_line FOREIGN KEY (grn_line_id)     REFERENCES goods_receipt_lines(id);
