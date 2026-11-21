package com.tmw.backend.audit.api;

import java.util.List;
import java.util.UUID;

public record AuditContext(
        UUID actorId,
        List<String> actorRoles,
        String requestId,
        String ipAddress
) {
    public AuditContext {
        actorRoles = (actorRoles == null || actorRoles.isEmpty()) ? List.of() : List.copyOf(actorRoles);
    }

    public static AuditContext systemFallback() {
        return new AuditContext(null, List.of("SYSTEM"), UUID.randomUUID().toString(), null);
    }
}