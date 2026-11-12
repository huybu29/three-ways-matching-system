package com.tmw.backend.matching.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Vòng đời của một MatchProposal.
 *
 * <pre>
 *   PROPOSED ──approve──▶ APPROVED ──post──▶ POSTED
 *      │
 *      ├──reject──▶ REJECTED
 *      └──cancel──▶ CANCELLED
 * </pre>
 *
 * REJECTED, CANCELLED, POSTED là trạng thái kết thúc (terminal).
 */
public enum MatchStatus {

    PROPOSED,
    APPROVED,
    POSTED,
    REJECTED,
    CANCELLED;

    /** Các trạng thái được phép chuyển tới từ trạng thái hiện tại. */
    public Set<MatchStatus> allowedNext() {
        return switch (this) {
            case PROPOSED -> Collections.unmodifiableSet(EnumSet.of(APPROVED, REJECTED, CANCELLED));
            case APPROVED -> Collections.unmodifiableSet(EnumSet.of(POSTED));
            case POSTED, REJECTED, CANCELLED -> Collections.emptySet();
        };
    }

    public boolean canTransitionTo(MatchStatus target) {
        return target != null && allowedNext().contains(target);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Đề xuất còn đang "giữ chỗ" hạn mức (reserved) trên dòng PO hay không.
     * Dùng cho BE-3 khi tính reserved, và cho BE-6 khi quyết định release.
     */
    public boolean holdsReservation() {
        return this == PROPOSED || this == APPROVED;
    }
}
