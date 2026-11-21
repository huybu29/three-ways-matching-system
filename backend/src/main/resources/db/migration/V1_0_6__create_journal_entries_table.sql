-- =============================================================================
-- Migration V1_0_6: Create journal_entries table for accounting posting results
-- Role: BE-6 (SoD Approval & Posting)
-- =============================================================================

CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id VARCHAR(64) NOT NULL,
    match_id UUID NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    vat_amount NUMERIC(19, 4) NOT NULL,
    payment_instruction_id VARCHAR(100),
    posted_by UUID NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT uq_journal_entries_journal_entry_id UNIQUE (journal_entry_id),
    CONSTRAINT uq_journal_entries_match_id UNIQUE (match_id),
    CONSTRAINT chk_journal_entries_total_amount_positive CHECK (total_amount >= 0),
    CONSTRAINT chk_journal_entries_vat_amount_positive CHECK (vat_amount >= 0),
    CONSTRAINT chk_journal_entries_vat_within_total CHECK (vat_amount <= total_amount)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_journal_entries_match_id ON journal_entries(match_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_posted_at ON journal_entries(posted_at);
CREATE INDEX IF NOT EXISTS idx_journal_entries_posted_by ON journal_entries(posted_by);
CREATE INDEX IF NOT EXISTS idx_journal_entries_payment_inst_id ON journal_entries(payment_instruction_id);

COMMENT ON TABLE journal_entries IS 'Stores accounting posting results (bút toán ghi sổ) committed by posters';
COMMENT ON COLUMN journal_entries.journal_entry_id IS 'Unique human-readable or business journal entry number';
COMMENT ON COLUMN journal_entries.match_id IS 'Associated MatchProposal UUID (one-to-one)';
COMMENT ON COLUMN journal_entries.total_amount IS 'Total posted amount with precision 19 scale 4';
COMMENT ON COLUMN journal_entries.vat_amount IS 'Total input VAT amount with precision 19 scale 4';
COMMENT ON COLUMN journal_entries.payment_instruction_id IS 'Reference ID for ERP/bank payment instructions';

