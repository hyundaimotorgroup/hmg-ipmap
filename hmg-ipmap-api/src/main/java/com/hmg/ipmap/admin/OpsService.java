package com.hmg.ipmap.admin;

import com.hmg.ipmap.admin.dto.OpsRequestDto;
import com.hmg.ipmap.admin.dto.OpsResponseDto;

public interface OpsService {
    /**
     * Dispatches the action specified in the request to the appropriate handler and returns the
     * result.
     *
     * @param opsRequestDto the request containing the action to execute and optional parameters
     * @return the {@link OpsResponseDto} produced by the executed action
     * @throws IllegalArgumentException if the request or its action is {@code null}
     * @throws UnsupportedOperationException if the action is not yet implemented
     */
    OpsResponseDto decideOpsAction(OpsRequestDto opsRequestDto);
}
