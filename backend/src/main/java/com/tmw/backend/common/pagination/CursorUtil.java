package com.tmw.backend.common.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CursorUtil {

    // Mã hóa giá trị gốc (ví dụ: ID = 100) thành Cursor chuỗi Base64
    public static String encodeCursor(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
    }

    // Giải mã Cursor chuỗi Base64 từ Client gửi lên về lại giá trị gốc (ví dụ: ID = 100)
    public static String decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCursor);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Ném ngoại lệ nếu Client gửi cursor bậy bạ không đúng chuẩn Base64
            throw new IllegalArgumentException("Invalid cursor format");
        }
    }
}