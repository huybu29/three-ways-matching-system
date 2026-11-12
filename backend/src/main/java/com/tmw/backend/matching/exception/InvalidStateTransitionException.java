package com.tmw.backend.matching.exception;

import com.tmw.backend.matching.entity.MatchStatus;

import java.util.UUID;

/**
 * Ném ra khi chuyển trạng thái MatchProposal không hợp lệ.
 * Map sang RFC 7807 Problem Detail: HTTP 409 Conflict, code INVALID_STATE_TRANSITION.
 * (Cùng kiểu với SodViolationException của BE-6 – BE-1 bắt trong GlobalExceptionHandler.)
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String code = "INVALID_STATE_TRANSITION";
    private final int status = 409;
    private final String title = "Invalid State Transition";
    private final String type = "https://api.example.com/errors/invalid-state-transition";

    private final UUID matchId;
    private final MatchStatus from;
    private final MatchStatus to;

    public InvalidStateTransitionException(UUID matchId, MatchStatus from, MatchStatus to) {
        super("MatchProposal %s cannot transition from %s to %s (allowed: %s)"
                .formatted(matchId, from, to, from == null ? "[]" : from.allowedNext()));
        this.matchId = matchId;
        this.from = from;
        this.to = to;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public UUID getMatchId() { return matchId; }
    public MatchStatus getFrom() { return from; }
    public MatchStatus getTo() { return to; }
}
