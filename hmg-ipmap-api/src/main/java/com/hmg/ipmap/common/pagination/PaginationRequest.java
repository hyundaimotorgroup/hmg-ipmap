package com.hmg.ipmap.common.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaginationRequest(
        @Schema(description = "Page size (items per page)", example = "20", nullable = true)
                Integer size,
        @Schema(description = "Zero-based page index", example = "0", nullable = true)
                Integer page) {
    public int sizeOrDefault() {
        return size != null ? size : 20;
    }

    public int pageOrDefault() {
        return page != null ? page : 0;
    }
}
