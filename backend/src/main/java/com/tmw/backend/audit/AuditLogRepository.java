package com.tmw.backend.audit;

import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog auditLog);
}