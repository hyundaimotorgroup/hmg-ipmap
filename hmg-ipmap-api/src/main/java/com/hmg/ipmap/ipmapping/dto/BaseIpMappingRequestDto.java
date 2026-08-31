package com.hmg.ipmap.ipmapping.dto;

import com.hmg.ipmap.location.dto.BaseLocationRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abstract base for all IP mapping create/update request bodies.
 *
 * <p>Carries the fields common to every data-provider variant (ipNotation, validPeriod,
 * attributes).
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseIpMappingRequestDto {

    @Schema(description = "The ip notation for the ip mapping", example = "192.0.2.0/24")
    @NotBlank(message = "ip_notation is required")
    private String ipNotation;

    @Schema(
            description = "The valid period of the ip mapping",
            example = "2025-12-08T11:02:48.790995Z")
    @Future(message = "valid_period must be a future date and time.")
    private Instant validPeriod;

    @Schema(
            description =
                    "Named attribute blocks for the ip mapping. Each key is an attribute type"
                            + " (e.g. LOCATION, POSTAL) and its value is a free-form JSON object."
                            + " New types can be added without changing the API contract.",
            example =
                    "{\"LOCATION\":{\"latitude\":37.6293,\"longitude\":-122.1163,\"time_zone\":\"America/Los_Angeles\"},"
                            + "\"POSTAL\":{\"code\":\"90001\"}}")
    private Map<String, Map<String, Object>> attributes;

    public abstract BaseLocationRequestDto getLocation();
}
