CREATE TABLE match_exception (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    invoice_line_id UUID,
    po_line_id UUID,
    match_id UUID,
    exception_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    message VARCHAR(1000) NOT NULL,
    details JSONB,
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_note VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_match_exception_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'WAIVED')),

    CONSTRAINT chk_match_exception_resolution_state
        CHECK (
            (status = 'OPEN' AND resolved_by IS NULL AND resolved_at IS NULL)
            OR
            (status IN ('RESOLVED', 'WAIVED') AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_match_exception_open_natural_key
    ON match_exception (
        invoice_id,
        COALESCE(invoice_line_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(po_line_id, '00000000-0000-0000-0000-000000000000'::uuid),
        exception_type
    )
    WHERE status = 'OPEN';

CREATE INDEX idx_match_exception_invoice_status
    ON match_exception (invoice_id, status);