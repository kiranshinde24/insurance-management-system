package com.insurance.util;

import com.insurance.dto.PageResponse;

import java.util.List;

/**
 * Slices an already-filtered/search-matched list into a page. Kept simple (in-memory slicing)
 * because the existing services build DTOs with joined data (customer name, agent name, etc.)
 * that doesn't map cleanly to a single JPQL query — this keeps pagination correct without a
 * risky rewrite of the whole data-access layer.
 */
public final class PaginationUtil {

    private PaginationUtil() {}

    public static <T> PageResponse<T> paginate(List<T> fullList, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 10 : size;

        int total = fullList.size();
        int totalPages = (int) Math.ceil((double) total / s);
        int from = Math.min(p * s, total);
        int to = Math.min(from + s, total);

        return PageResponse.<T>builder()
                .content(fullList.subList(from, to))
                .page(p)
                .size(s)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }
}
