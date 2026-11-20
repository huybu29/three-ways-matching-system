package com.tmw.backend.posting.exception;

import java.util.UUID;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import lombok.Getter;

@Getter
public class SodViolationException extends BusinessException {

    private final UUID actorId;
    private final UUID conflictingActorId;
    private final String action;

    public SodViolationException(String message, UUID actorId, UUID conflictingActorId, String action) {
        super(ErrorCode.SOD_VIOLATION, message);
        this.actorId = actorId;
        this.conflictingActorId = conflictingActorId;
        this.action = action;
    }
}