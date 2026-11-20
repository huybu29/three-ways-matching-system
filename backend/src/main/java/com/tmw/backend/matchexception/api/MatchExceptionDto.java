package com.tmw.backend.matchexception.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MatchExceptionDto(
        UUID id,
        UUID invoiceId,
        UUID invoiceLineId,
        UUID poLineId,
        UUID matchId,
        ExceptionType exceptionType,
        ExceptionStatus status,
        String message,
        Map<String, Object> details,
        UUID resolvedBy,
        Instant resolvedAt,
        String resolutionNote,
        Long version
) {
}