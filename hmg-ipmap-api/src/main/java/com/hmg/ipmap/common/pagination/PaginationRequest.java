package com.hmg.ipmap.common.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PaginationRequest(
        @Schema(
                        description = "Page size (1–100 items per page)",
                        example = "20",
                        nullable = true,
                        minimum = "1",
                        maximum = "100")
                @Min(value = 1, message = "Page size must be at least 1.")
                @Max(value = 100, message = "Page size must not exceed 100.")
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
