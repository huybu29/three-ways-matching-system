package com.tmw.backend.common.pagination;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CursorUtilTest {

    @Test
    void shouldEncodeAndDecodeSuccessfully() {
        String rawId = "1050";
        
        // Test mã hóa
        String encoded = CursorUtil.encodeCursor(rawId);
        assertNotNull(encoded);
        assertNotEquals(rawId, encoded); // Chắc chắn chuỗi đã bị thay đổi

        // Test giải mã
        String decoded = CursorUtil.decodeCursor(encoded);
        assertEquals(rawId, decoded); // Giải mã phải ra đúng ID ban đầu
    }

    @Test
    void shouldReturnNull_WhenEncodingNullOrBlank() {
        assertNull(CursorUtil.encodeCursor(null));
        assertNull(CursorUtil.encodeCursor("   "));
    }

    @Test
    void shouldReturnNull_WhenDecodingNullOrBlank() {
        assertNull(CursorUtil.decodeCursor(null));
        assertNull(CursorUtil.decodeCursor("   "));
    }

    @Test
    void shouldThrowException_WhenDecodingInvalidBase64() {
        String invalidCursor = "ThisIsNotBase64!@#";
        
        // Phải ném ra lỗi IllegalArgumentException nếu client gửi lên cursor bậy bạ
        assertThrows(IllegalArgumentException.class, () -> {
            CursorUtil.decodeCursor(invalidCursor);
        });
    }
}