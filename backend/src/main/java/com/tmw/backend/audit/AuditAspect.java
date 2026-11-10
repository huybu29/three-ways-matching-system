package com.tmw.backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // 1. Tự động lấy Request ID và IP Address từ HTTP Request
        String requestId = null;
        String ipAddress = null;
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpServletRequest request = attr.getRequest();
            requestId = request.getHeader("X-Request-ID");
            ipAddress = request.getRemoteAddr();
        }

        // 2. Lấy thông tin Actor (User đang đăng nhập và Roles)
        List<String> roles = List.of();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
            // Lấy thông tin user tùy theo cấu hình security của dự án
        }

        // Thực thi phương thức gốc
        Object result = joinPoint.proceed();

        // 3. Tự động chụp và lưu bản ghi Audit Log vào database
        AuditLog log = new AuditLog();
        log.setOccurredAt(Instant.now());
        log.setAction(auditable.action());
        log.setEntityType(auditable.entityType());
        log.setEntityId(UUID.randomUUID());

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("status", "SUCCESS");
        log.setAfterData(afterData);

        log.setRequestId(requestId);
        log.setIpAddress(ipAddress);
        log.setActorRoles(roles);

        auditLogRepository.save(log);

        return result;
    }
}