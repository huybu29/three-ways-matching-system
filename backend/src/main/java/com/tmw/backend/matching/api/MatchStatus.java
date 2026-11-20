package com.tmw.backend.matching.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum MatchStatus {
    PROPOSED,
    APPROVED,
    POSTED,
    REJECTED,
    CANCELLED;

    public Set<MatchStatus> allowedNextStates() {
        EnumSet<MatchStatus> next = switch (this) {
            case PROPOSED -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(POSTED, REJECTED, CANCELLED);
            case POSTED, REJECTED, CANCELLED -> EnumSet.noneOf(MatchStatus.class);
        };
        return Collections.unmodifiableSet(next);
    }

    public boolean canTransitionTo(MatchStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    public boolean isActive() {
        return this == PROPOSED || this == APPROVED;
    }

    public boolean isImmutable() {
        return this == POSTED;
    }

    public boolean requiresReason() {
        return this == REJECTED || this == CANCELLED;
    }
    public Set<MatchStatus> allowedNext() {
    return allowedNextStates();
}
}