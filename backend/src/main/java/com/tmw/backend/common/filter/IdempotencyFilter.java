package com.tmw.backend.common.filter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if ("POST".equalsIgnoreCase(req.getMethod())) {
            String idempotencyKey = req.getHeader("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                res.sendError(HttpStatus.BAD_REQUEST.value(), "Missing Idempotency-Key header");
                return;
            }

            String redisKey = "idempotency:" + idempotencyKey;
            Boolean isNewKey = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));

            if (Boolean.FALSE.equals(isNewKey)) {
                res.setStatus(HttpStatus.CONFLICT.value());
                res.setContentType("application/problem+json");
                Map<String, Object> problem = Map.of(
                    "type", URI.create("https://api.example.com/errors/idempotency-key-reused"),
                    "title", "Khóa chống trùng lặp đã được sử dụng",
                    "status", HttpStatus.CONFLICT.value(),
                    "code", "IDEMPOTENCY_KEY_REUSED",
                    "detail", "Idempotency-Key này đã được gửi trong 24 giờ qua.",
                    "trace_id", UUID.randomUUID().toString()
                );
                res.getWriter().write(objectMapper.writeValueAsString(problem));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}