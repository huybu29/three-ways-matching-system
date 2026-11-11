package com.tmw.backend.masterdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Trang kết quả theo con trỏ: {@code {items, next_cursor}} (PageMeta trong contract).
 * Tạm đặt trong masterdata vì tiện ích phân trang chung của BE-1 chưa có trên develop;
 * khi BE-1 merge thì đổi sang dùng bản chung và xóa file này.
 */
public record CursorPage<T>(List<T> items, @JsonProperty("next_cursor") String nextCursor) {

    public record Cursor(OffsetDateTime createdAt, UUID id) {}

    public static String encode(OffsetDateTime createdAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException nếu con trỏ sai định dạng */
    public static Cursor decode(String cursor) {
        try {
            String[] p = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new Cursor(OffsetDateTime.parse(p[0]), UUID.fromString(p[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cursor không hợp lệ", e);
        }
    }
}
