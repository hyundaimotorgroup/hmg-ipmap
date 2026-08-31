package com.hmg.ipmap.admin.dto;

import com.hmg.ipmap.common.enums.OpsAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Response payload returned by an administrative operation.
 *
 * <p>Contains the echoed action, a human-readable result message, and an optional detail object
 * whose structure varies by action type.
 */
@Data
public class OpsResponseDto {
    @Schema(description = "The admin action", example = "CDC_SERVICE_START")
    private OpsAction action;

    @Schema(description = "Response message", example = "Action executed successfully")
    private String message;

    @Schema(description = "Optional response detail", nullable = true)
    private Object detail;
}
