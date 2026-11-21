package com.tmw.backend.audit.web;

import com.tmw.backend.audit.api.AuditContext;
import com.tmw.backend.audit.api.AuditContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AuditRequestContextFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String requestId = extractRequestId(request);
            String ipAddress = extractClientIp(request);
            UUID actorId = extractActorId();
            List<String> roles = extractActorRoles();

            response.setHeader(HEADER_REQUEST_ID, requestId);
            AuditContextHolder.setContext(new AuditContext(actorId, roles, requestId, ipAddress));

            filterChain.doFilter(request, response);
        } finally {
            AuditContextHolder.clear();
        }
    }

    private static String extractRequestId(HttpServletRequest request) {
        String header = request.getHeader(HEADER_REQUEST_ID);
        return (header != null && !header.isBlank()) ? header.trim() : UUID.randomUUID().toString();
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static UUID extractActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> extractActorRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}