package com.tmw.backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String requestId = null;
        String ipAddress = null;
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpServletRequest request = attr.getRequest();
            requestId = request.getHeader("X-Request-ID");
            ipAddress = request.getRemoteAddr();
        }

        List<String> roles = List.of();
        UUID actorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
        }

        Map<String, Object> beforeData = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            beforeData.put("input_args", args[0]);
        }

        Object result = joinPoint.proceed();

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("result", result);

        AuditLog log = AuditLog.builder()
                .occurredAt(Instant.now())
                .action(auditable.action())
                .entityType(auditable.entityType())
                .entityId(UUID.randomUUID())
                .actorId(actorId)
                .actorRoles(roles)
                .beforeData(beforeData)
                .afterData(afterData)
                .requestId(requestId)
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(log);

        return result;
    }
}