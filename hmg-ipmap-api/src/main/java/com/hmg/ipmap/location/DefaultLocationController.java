package com.hmg.ipmap.location;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.location.dto.BaseLocationResponseDto;
import com.hmg.ipmap.location.dto.DefaultLocationRequestDto;
import com.hmg.ipmap.location.dto.DefaultLocationResponseDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.location.enums.LocationLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Location controller for the {@code default} data-provider.
 *
 * <p>Active when {@code app.data-provider=default} or when the property is not set. Accepts a
 * single {@code province} field in the create request body and returns a {@link
 * DefaultLocationResponseDto} with the same shape. All other endpoints are inherited from {@link
 * BaseLocationController}.
 */
@RestController
@ConditionalOnProperty(name = "app.data-provider", havingValue = "default", matchIfMissing = true)
public class DefaultLocationController extends BaseLocationController {

    public DefaultLocationController(LocationService service) {
        super(service);
    }

    @Override
    protected DefaultLocationResponseDto toResponse(Map<LocationLevel, LocationDto> internal) {
        DefaultLocationResponseDto dto = new DefaultLocationResponseDto();
        dto.setContinent(internal.get(DefaultLocationLevel.CONTINENT));
        dto.setCountry(internal.get(DefaultLocationLevel.COUNTRY));
        dto.setRegion(internal.get(DefaultLocationLevel.REGION));
        dto.setCity(internal.get(DefaultLocationLevel.CITY));
        return dto;
    }

    @Override
    protected Optional<LocationLevel> resolveLevel(String name) {
        return Arrays.stream(DefaultLocationLevel.values())
                .filter(l -> l.name().equals(name))
                .findFirst()
                .map(l -> (LocationLevel) l);
    }

    @Operation(
            summary = "Add a new location",
            description =
                    "Add a new location (default provider: continent, country, province, city)",
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
                                                                DefaultLocationResponseDto.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflict location",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class))),
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
    public BaseLocationResponseDto create(@Valid @RequestBody DefaultLocationRequestDto source) {
        return toResponse(service.create(source.getAllLocationMap()));
    }
}
