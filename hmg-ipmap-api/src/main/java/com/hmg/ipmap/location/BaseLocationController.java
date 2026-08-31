package com.hmg.ipmap.location;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.location.dto.BaseLocationResponseDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.enums.LocationLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Abstract base controller for {@code /api/v1/locations}.
 *
 * <p>Provides the read, update, and delete endpoints that are identical across all data-provider
 * variants. Concrete subclasses supply only the {@code POST /} create endpoint, whose request body
 * shape varies per provider.
 *
 * <p>Spring MVC inherits {@link RequestMapping} and all mapped methods from this class, so
 * subclasses only need {@code @RestController} and {@code @ConditionalOnProperty}.
 */
@RequestMapping("/api/v1/locations")
public abstract class BaseLocationController {

    protected final LocationService service;

    protected BaseLocationController(LocationService service) {
        this.service = service;
    }

    /**
     * Converts the location hierarchy map into the provider-specific response shape. Called by
     * every read/write endpoint so that subclasses control the response structure in one place.
     */
    protected abstract BaseLocationResponseDto toResponse(Map<LocationLevel, LocationDto> internal);

    /**
     * Resolves a {@link LocationLevel} constant from its {@link LocationLevel#name()} string.
     * Returns {@link Optional#empty()} when the name does not match any constant in the
     * provider-specific enum, allowing callers to skip unknown levels gracefully.
     */
    protected abstract Optional<LocationLevel> resolveLevel(String name);

    @Operation(
            summary = "Delete a location",
            description = "Delete a location by location id",
            responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Successful response",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "Location not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @PathVariable Long id) {
        service.delete(id);
    }

    @Operation(
            summary = "Find a location by location id",
            description = "Find a location by location id",
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
                                                                BaseLocationResponseDto.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Location not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @GetMapping("/{id}")
    public BaseLocationResponseDto findById(@Valid @PathVariable Long id) {
        Map<LocationLevel, LocationDto> levelMap =
                service.findById(id).entrySet().stream()
                        .flatMap(
                                e ->
                                        resolveLevel(e.getKey())
                                                .map(level -> Map.entry(level, e.getValue()))
                                                .stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return toResponse(levelMap);
    }

    @Operation(
            summary = "Get all locations",
            description = "Fetch all locations",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                PaginationResponse
                                                                                        .class))))
            })
    @GetMapping
    public PaginationResponse<LocationDto> findAll(
            @ParameterObject @Valid @ModelAttribute PaginationRequest req) {
        return service.searchWithPagination(req);
    }

    @Operation(
            summary = "Update a location",
            description = "Update a location",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = LocationDto.class))),
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
    @PutMapping("/{id}")
    public LocationDto update(
            @Valid @PathVariable Long id, @Valid @RequestBody LocationDto source) {
        return service.update(id, source);
    }
}
