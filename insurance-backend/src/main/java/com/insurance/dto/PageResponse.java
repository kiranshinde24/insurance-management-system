package com.insurance.dto;

import lombok.*;

import java.util.List;

/** Generic wrapper for paginated list responses. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;          // zero-based
    private int size;
    private long totalElements;
    private int totalPages;
}
