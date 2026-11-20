package com.tmw.backend.common.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
@lombok.Builder
public record PageMeta(
        @JsonProperty("next_cursor")
        String nextCursor,

        @JsonProperty("has_next")
        boolean hasNext,

        @JsonProperty("limit")
        int limit
) {
    public static PageMeta empty() {
        return new PageMeta(null, false, 0);
    }

    public record PageResponse<T>(
            @JsonProperty("next_cursor")
            String nextCursor,
            List<T> items
    ) {
    }
}