package com.hmg.ipmap.common.pagination;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PaginationResponse<T>(
        @ArraySchema(arraySchema = @Schema(description = "Data in the current page"))
                List<T> content,
        @Schema(description = "Is this the last page?", example = "true") boolean last,
        @Schema(description = "Total number of items across all pages", example = "257")
                long totalElements,
        @Schema(description = "Total number of pages", example = "13") int totalPages,
        @Schema(description = "Is this the first page?", example = "true") boolean first,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(description = "Current zero-based page index", example = "0") int number,
        @Schema(description = "Number of items in the current page", example = "20")
                int numberOfElements,
        @Schema(description = "Is the current page empty?", example = "false") boolean empty) {}
