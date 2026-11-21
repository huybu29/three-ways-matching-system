package com.tmw.backend.common.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

@Component
public class CursorCodec {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final String DELIMITER = "|";

    private final ObjectMapper objectMapper;

    @Autowired
    public CursorCodec() {
        this.objectMapper = new ObjectMapper();
    }

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public record CompositeCursor(
            Instant timestamp,
            UUID id
    ) {
    }

    public record KeysetCursor(
            Instant createdAt,
            UUID id
    ) {
        public Instant timestamp() {
            return createdAt;
        }
    }

    public String encode(Instant createdAt, UUID id) {
        return encodeCompositeCursor(createdAt, id);
    }

    public KeysetCursor decode(String cursor) {
        CompositeCursor c = decodeCompositeCursor(cursor);
        return c == null ? null : new KeysetCursor(c.timestamp(), c.id());
    }

    public <T> PageMeta.PageResponse<T> buildPage(
            List<T> items,
            int limit,
            Function<T, String> cursorExtractor
    ) {
        if (items == null || items.isEmpty()) {
            return new PageMeta.PageResponse<>(null, List.of());
        }
        int safeLimit = normalizeLimit(limit);
        boolean hasNext = items.size() > safeLimit;
        List<T> pageItems = hasNext ? List.copyOf(items.subList(0, safeLimit)) : List.copyOf(items);
        String nextCursor = hasNext ? cursorExtractor.apply(pageItems.get(pageItems.size() - 1)) : null;
        return new PageMeta.PageResponse<>(nextCursor, pageItems);
    }

    public static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public static String encodeCompositeCursor(Instant timestamp, UUID id) {
        if (timestamp == null || id == null) {
            return null;
        }
        String raw = timestamp.toString() + DELIMITER + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CompositeCursor decodeCompositeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor.trim());
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cursor phân trang không hợp lệ");
            }
            return new CompositeCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cursor phân trang không hợp lệ");
        }
    }

    public static String encodeUuidCursor(UUID id) {
        if (id == null) {
            return null;
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static UUID decodeUuidCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor.trim());
            return UUID.fromString(new String(decodedBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cursor phân trang không hợp lệ");
        }
    }
}