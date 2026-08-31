package com.hmg.ipmap.admin.dto;

import com.hmg.ipmap.common.enums.OpsAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

/**
 * Request payload for an administrative operation.
 *
 * <p>Carries the target {@link com.hmg.ipmap.common.enums.OpsAction} and an optional parameter map
 * for actions that require additional configuration.
 */
@Data
public class OpsRequestDto {

    @Schema(description = "The admin action", example = "CDC_SERVICE_START")
    private OpsAction action;

    @Schema(description = "The additional parameters to run the action", nullable = true)
    private Map<String, Object> params;
}
