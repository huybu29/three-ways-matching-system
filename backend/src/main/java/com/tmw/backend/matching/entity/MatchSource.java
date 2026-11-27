package com.tmw.backend.matching.entity;

/** Nguồn tạo đề xuất đối chiếu. */
public enum MatchSource {
    /** Sinh từ POST /invoices/{invoiceId}/match-runs. */
    AUTO,
    /** Người dùng (matcher) ghép tay qua POST /matches. */
    MANUAL
}
