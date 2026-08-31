package com.hmg.ipmap.admin;

import com.hmg.ipmap.admin.dto.OpsRequestDto;
import com.hmg.ipmap.admin.dto.OpsResponseDto;
import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing administrative operations for system management.
 *
 * <p>Mapped to {@code /api/v1/admin}. Delegates all business logic to {@link OpsServiceImpl}.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;

    @Operation(
            summary = "Admin operation to manage the system",
            description =
                    "This API to manage system such as: Start or stop the CDC engine, reset cdc offset, etc",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OpsResponseDto.class),
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                                        {
                                        \t"action": "CDC_SERVICE_STATUS",
                                        \t"message": "Action executed successfully",
                                        \t"detail": {
                                        \t\t"running": true,
                                        \t\t"last_failure": null,
                                        \t\t"last_event_time_millis": null,
                                        \t\t"idle_for_ms": null,
                                        \t\t"toggle_off": false
                                        \t}
                                        }
                                        """))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(implementation = GlobalErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                                        {
                                        \t"timestamp": "2025-12-18 13:54:33.166+0700",
                                        \t"status": 400,
                                        \t"error": "Bad Request",
                                        \t"message": "Malformed JSON request. Please check the request body format.",
                                        \t"path": "/api/v1/admin/ops",
                                        \t"detail": "JSON parse error: Cannot deserialize value of type `enums.OpsAction` "
                                        }
                                        """)))
            })
    @PostMapping("/ops")
    public ResponseEntity<OpsResponseDto> create(
            @Parameter(description = "Ops request object") @Valid @RequestBody
                    OpsRequestDto opsRequestDto) {
        OpsResponseDto opsResponseDto = opsService.decideOpsAction(opsRequestDto);
        return ResponseEntity.ok(opsResponseDto);
    }
}
