package com.tmw.backend.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditLogCapturedEvent(
        Instant occurredAt,
        UUID actorId,
        List<String> actorRoles,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String requestId,
        String ipAddress
) {
}