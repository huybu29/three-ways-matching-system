package com.tmw.backend.audit.listener;

import com.tmw.backend.audit.api.AuditLogCapturedEvent;
import com.tmw.backend.audit.entity.AuditLog;
import com.tmw.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogRepository auditLogRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onAuditLogCaptured(AuditLogCapturedEvent event) {


        AuditLog logEntry = AuditLog.builder()
                .occurredAt(event.occurredAt())
                .actorId(event.actorId())
                .actorRoles(event.actorRoles())
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .beforeData(event.beforeData())
                .afterData(event.afterData())
                .requestId(event.requestId())
                .ipAddress(event.ipAddress())
                .build();

        auditLogRepository.save(logEntry);
    }
}