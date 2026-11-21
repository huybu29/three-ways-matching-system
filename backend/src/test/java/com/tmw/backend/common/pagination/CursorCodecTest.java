package com.tmw.backend.common.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    private CursorCodec cursorCodec;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cursorCodec = new CursorCodec(objectMapper);
    }

    @Test
    void shouldEncodeAndDecodeKeysetCursorRoundTrip() {
        Instant now = Instant.parse("2026-10-15T08:30:00Z");
        UUID id = UUID.fromString("3f0c9c4e-1111-4a2b-9c1d-000000000001");

        String encoded = cursorCodec.encode(now, id);
        assertThat(encoded).isNotBlank();

        CursorCodec.KeysetCursor decoded = cursorCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.timestamp()).isEqualTo(now);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void shouldReturnNullWhenDecodingNullOrBlankCursor() {
        assertThat(cursorCodec.decode(null)).isNull();
        assertThat(cursorCodec.decode("   ")).isNull();
    }

    @Test
    void shouldThrowValidationErrorWhenCursorIsCorrupted() {
        assertThatThrownBy(() -> cursorCodec.decode("not-a-valid-base64-cursor!!!"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldNormalizeLimitProperly() {
        assertThat(cursorCodec.normalizeLimit(null)).isEqualTo(CursorCodec.DEFAULT_LIMIT);
        assertThat(cursorCodec.normalizeLimit(1)).isEqualTo(1);
        assertThat(cursorCodec.normalizeLimit(200)).isEqualTo(200);

        assertThatThrownBy(() -> cursorCodec.normalizeLimit(0))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> cursorCodec.normalizeLimit(201))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldBuildPageWithNextCursorOnlyWhenHasMoreItems() {
        List<String> twoItems = List.of("item-1", "item-2");
        PageMeta.PageResponse<String> exactPage = cursorCodec.buildPage(twoItems, 2, s -> "cursor-" + s);
        assertThat(exactPage.items()).containsExactly("item-1", "item-2");
        assertThat(exactPage.nextCursor()).isNull();

        List<String> threeItems = List.of("item-1", "item-2", "item-3");
        PageMeta.PageResponse<String> hasMorePage = cursorCodec.buildPage(threeItems, 2, s -> "cursor-" + s);
        assertThat(hasMorePage.items()).containsExactly("item-1", "item-2");
        assertThat(hasMorePage.nextCursor()).isEqualTo("cursor-item-2");
    }
}