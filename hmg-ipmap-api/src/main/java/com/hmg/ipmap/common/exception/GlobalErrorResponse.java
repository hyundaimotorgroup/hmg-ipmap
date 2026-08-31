package com.hmg.ipmap.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GlobalErrorResponse {
    @Schema(description = "The current timestamp", example = "2025-12-03 16:34:41.733+0900")
    private String timestamp;

    @Schema(description = "HTTP status response", example = "409")
    private int status;

    @Schema(description = "HTTP error response", example = "Conflict")
    private String error;

    @Schema(description = "Error message", example = "Error description")
    private String message;

    @Schema(description = "Endpoint path", example = "/api/v1/users")
    private String path;
}
