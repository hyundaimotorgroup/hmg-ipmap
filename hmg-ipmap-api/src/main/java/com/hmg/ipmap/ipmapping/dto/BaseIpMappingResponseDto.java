package com.hmg.ipmap.ipmapping.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.location.dto.BaseLocationResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Abstract base for all IP mapping response bodies. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class BaseIpMappingResponseDto {

    @Schema(description = "The ip mapping id", example = "123")
    private Long id;

    @Schema(description = "The scope of the ip mapping", example = "GLOBAL")
    private Scope scope;

    @Schema(description = "The ip notation of the ip mapping", example = "192.0.2.0/24")
    private String ipNotation;

    @Schema(
            description = "The valid period of the ip mapping",
            example = "2025-12-08T11:02:48.790995Z")
    private Instant validPeriod;

    @Schema(
            description =
                    "Named attribute blocks for the ip mapping. Each key is an attribute type"
                            + " (e.g. LOCATION, POSTAL) and its value is a free-form JSON object.",
            example =
                    "{\"LOCATION\":{\"latitude\":37.6293,\"longitude\":-122.1163,\"time_zone\":\"America/Los_Angeles\"},"
                            + "\"POSTAL\":{\"code\":\"90001\"}}")
    private Map<String, Map<String, Object>> attributes;

    public abstract BaseLocationResponseDto getLocation();
}
