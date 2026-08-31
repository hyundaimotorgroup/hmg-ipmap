package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.ipmapping.dto.BaseIpMappingResponseDto;
import com.hmg.ipmap.ipmapping.dto.DefaultIpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.DefaultIpMappingResponseDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingLocationDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.dto.DefaultLocationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * IP mapping controller for the {@code default} data-provider.
 *
 * <p>Active when {@code app.data-provider=default} or when the property is not set. Accepts a
 * single {@code region} field inside the {@code location} block for both create and update. All
 * other endpoints are inherited from {@link BaseIpMappingController}.
 */
@RestController
@ConditionalOnProperty(name = "app.data-provider", havingValue = "default", matchIfMissing = true)
public class DefaultIpMappingController
        extends BaseIpMappingController<DefaultIpMappingRequestDto> {

    public DefaultIpMappingController(IpMappingService service) {
        super(service);
    }

    @Override
    protected IpMappingRequestDto toServiceDto(DefaultIpMappingRequestDto source) {
        return new IpMappingRequestDto(
                source.getIpNotation(),
                source.getValidPeriod(),
                source.getAttributes(),
                source.getLocation(),
                null,
                null);
    }

    @Override
    protected DefaultIpMappingResponseDto toResponse(IpMappingResponseDto internal) {
        DefaultIpMappingResponseDto dto = new DefaultIpMappingResponseDto();
        dto.setId(internal.getId());
        dto.setScope(internal.getScope());
        dto.setIpNotation(internal.getIpNotation());
        dto.setValidPeriod(internal.getValidPeriod());
        dto.setAttributes(internal.getAttributes());

        IpMappingLocationDto loc = internal.getLocation();
        if (loc != null) {
            DefaultLocationResponseDto location = new DefaultLocationResponseDto();
            location.setContinent(loc.getContinent());
            location.setCountry(loc.getCountry());
            location.setCity(loc.getCity());
            location.setRegion(
                    loc.getAdditionalLocations() != null && !loc.getAdditionalLocations().isEmpty()
                            ? loc.getAdditionalLocations().getFirst()
                            : null);
            dto.setLocation(location);
        }
        return dto;
    }

    @Operation(
            summary = "Create an ip mapping",
            description =
                    "Create an ip mapping (default provider: province instead of additionalLocations)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                DefaultIpMappingResponseDto
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad request",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PostMapping
    public BaseIpMappingResponseDto create(@Valid @RequestBody DefaultIpMappingRequestDto source) {
        return toResponse(service.create(toServiceDto(source)));
    }

    @Operation(
            summary = "Update an ip mapping",
            description =
                    "Update an ip mapping (default provider: province instead of additionalLocations)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                DefaultIpMappingResponseDto
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad request",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Ip mapping not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PutMapping("/{id}")
    public BaseIpMappingResponseDto update(
            @Valid @PathVariable Long id, @Valid @RequestBody DefaultIpMappingRequestDto source) {
        return toResponse(service.update(id, toServiceDto(source)));
    }
}
