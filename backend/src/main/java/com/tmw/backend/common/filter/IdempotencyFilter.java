package com.tmw.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.common.entity.IdempotencyKeyRecord;
import com.tmw.backend.common.entity.IdempotencyKeyRecord.Status;
import com.tmw.backend.common.repository.IdempotencyKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Idempotency-Key";
    private static final Duration TTL = Duration.ofHours(24);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(HEADER_NAME);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            writeProblem(
                    response,
                    HttpStatus.BAD_REQUEST,
                    "https://api.example.com/errors/missing-idempotency-key",
                    "Thiếu Idempotency-Key",
                    "MISSING_IDEMPOTENCY_KEY",
                    "Header Idempotency-Key là bắt buộc cho các request ghi dữ liệu."
            );
            return;
        }

        String trimmedKey = idempotencyKey.trim();
        Instant now = Instant.now();

        Optional<IdempotencyKeyRecord> existingOpt = repository.findByIdempotencyKey(trimmedKey);
        if (existingOpt.isPresent()) {
            IdempotencyKeyRecord existing = existingOpt.get();

            if (!existing.getExpiresAt().isAfter(now)) {
                repository.delete(existing);
                repository.flush();
            } else if (existing.getStatus() == Status.COMPLETED && existing.getResponseStatus() != null) {
                response.setStatus(existing.getResponseStatus());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                if (existing.getResponseBody() != null) {
                    response.getWriter().write(existing.getResponseBody());
                }
                return;
            } else {
                writeProblem(
                        response,
                        HttpStatus.CONFLICT,
                        "https://api.example.com/errors/idempotency-key-reused",
                        "Khóa chống trùng lặp đang được xử lý hoặc đã sử dụng",
                        "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key này đã được gửi trong vòng 24 giờ."
                );
                return;
            }
        }

        IdempotencyKeyRecord record = IdempotencyKeyRecord.builder()
                .idempotencyKey(trimmedKey)
                .httpMethod(request.getMethod().toUpperCase())
                .requestPath(request.getRequestURI())
                .status(Status.PROCESSING)
                .createdAt(now)
                .expiresAt(now.plus(TTL))
                .build();

        try {
            record = repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            writeProblem(
                    response,
                    HttpStatus.CONFLICT,
                    "https://api.example.com/errors/idempotency-key-reused",
                    "Khóa chống trùng lặp đã tồn tại",
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key này đang được xử lý bởi một request khác."
            );
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);

            byte[] bodyBytes = wrappedResponse.getContentAsByteArray();
            String bodyText = new String(bodyBytes, StandardCharsets.UTF_8);

            record.setResponseStatus(wrappedResponse.getStatus());
            record.setResponseBody(bodyText);
            record.setStatus(wrappedResponse.getStatus() < 500 ? Status.COMPLETED : Status.FAILED);
            repository.save(record);

            wrappedResponse.copyBodyToResponse();
        } catch (Exception ex) {
            record.setStatus(Status.FAILED);
            record.setResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            repository.save(record);
            throw ex;
        }
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String typeUri,
            String title,
            String code,
            String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> problem = Map.of(
                "type", URI.create(typeUri),
                "title", title,
                "status", status.value(),
                "code", code,
                "detail", detail
        );
        objectMapper.writeValue(response.getWriter(), problem);
    }
}