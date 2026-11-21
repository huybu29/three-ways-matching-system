package com.tmw.backend.audit.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditLogPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(
            String entityType,
            String action,
            UUID entityId,
            Map<String, Object> beforeData,
            Map<String, Object> afterData
    ) {
        AuditContext ctx = AuditContextHolder.getContextOrFallback();
        eventPublisher.publishEvent(new AuditLogCapturedEvent(
                Instant.now(),
                ctx.actorId(),
                ctx.actorRoles(),
                action,
                entityType,
                entityId,
                beforeData,
                afterData,
                ctx.requestId(),
                ctx.ipAddress()
        ));
    }
}