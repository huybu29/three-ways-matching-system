package com.tmw.backend.common.pagination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CursorResponse<T> {

    private List<T> data;
    private PageMeta meta;

    public static <T> CursorResponse<T> of(List<T> fetchedItems, int limit, Function<T, String> cursorExtractor) {
        boolean hasNext = fetchedItems.size() > limit;
        List<T> pageData = hasNext ? fetchedItems.subList(0, limit) : fetchedItems;

        String nextCursor = null;
        if (hasNext && !pageData.isEmpty()) {
            nextCursor = cursorExtractor.apply(pageData.get(pageData.size() - 1));
        }

        PageMeta meta = PageMeta.builder()
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .limit(limit)
                .build();

        return new CursorResponse<>(pageData, meta);
    }
}