-- Bản sao tối thiểu các bảng của BE-3 (khớp entity PurchaseOrder / PurchaseOrderLine / PoLineCapacity)
-- chỉ dùng cho test CandidateFinder khi BE-3 chưa có migration tạo bảng.
-- Khi migration của BE-3 có trên develop, test nên chuyển sang chạy migration thật.

CREATE TABLE purchase_order (
    id           UUID          PRIMARY KEY,
    po_number    VARCHAR(50)   NOT NULL,
    supplier_id  UUID          NOT NULL,
    order_date   DATE          NOT NULL,
    currency     VARCHAR(10)   NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status       VARCHAR(30)   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_purchase_order_po_number UNIQUE (po_number)
);

CREATE TABLE po_line (
    id                UUID          PRIMARY KEY,
    purchase_order_id UUID          NOT NULL REFERENCES purchase_order (id),
    line_no           INTEGER       NOT NULL,
    item_code         VARCHAR(100)  NOT NULL,
    description       VARCHAR(500),
    ordered_qty       NUMERIC(19,4) NOT NULL,
    unit_price        NUMERIC(19,4) NOT NULL,
    vat_rate          NUMERIC(19,4) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_po_line_po_id_line_no UNIQUE (purchase_order_id, line_no)
);

CREATE TABLE po_line_capacity (
    po_line_id       UUID          PRIMARY KEY REFERENCES po_line (id),
    ordered_qty      NUMERIC(19,4) NOT NULL,
    allocated_qty    NUMERIC(19,4) NOT NULL DEFAULT 0,
    reserved_qty     NUMERIC(19,4) NOT NULL DEFAULT 0,
    received_qty     NUMERIC(19,4) NOT NULL DEFAULT 0,
    ordered_amount   NUMERIC(19,4) NOT NULL,
    allocated_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reserved_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
    received_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
    version          BIGINT        NOT NULL DEFAULT 0
);
