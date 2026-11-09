package com.tmw.backend.posting.exception;

import java.util.UUID;
import lombok.Getter;

/**
 * Exception thrown when a Segregation of Duties (SoD) rule is violated.
 * Maps to RFC 7807 Problem Detail with HTTP 403 Forbidden and error code SOD_VIOLATION.
 */
@Getter
public class SodViolationException extends RuntimeException {

    private final String code = "SOD_VIOLATION";
    private final int status = 403;
    private final String title = "Segregation of Duties Violation";
    private final String type = "https://api.example.com/errors/sod-violation";

    private final UUID actorId;
    private final UUID conflictingUserId;
    private final String action;

    public SodViolationException(String message) {
        super(message);
        this.actorId = null;
        this.conflictingUserId = null;
        this.action = null;
    }

    public SodViolationException(String message, UUID actorId, UUID conflictingUserId, String action) {
        super(message);
        this.actorId = actorId;
        this.conflictingUserId = conflictingUserId;
        this.action = action;
    }
}

