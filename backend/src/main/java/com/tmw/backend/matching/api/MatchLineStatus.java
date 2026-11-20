package com.tmw.backend.matching.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum MatchLineStatus {

    RESERVED,
    ALLOCATED,
    RELEASED;

    public Set<MatchLineStatus> allowedNextStates() {
        EnumSet<MatchLineStatus> next = switch (this) {
            case RESERVED -> EnumSet.of(ALLOCATED, RELEASED);
            case ALLOCATED, RELEASED -> EnumSet.noneOf(MatchLineStatus.class);
        };
        return Collections.unmodifiableSet(next);
    }

    public boolean canTransitionTo(MatchLineStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    public boolean isTerminal() {
        return allowedNextStates().isEmpty();
    }

    public boolean holdsCapacity() {
        return this == RESERVED || this == ALLOCATED;
    }
}