package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.dto.BaseIpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.BaseIpMappingResponseDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Abstract base controller for {@code /api/v1/ip-mappings}.
 *
 * <p>Provides the read, delete, and rebuild endpoints that are identical across all data-provider
 * variants. Concrete subclasses supply the {@code POST /} and {@code PUT /{id}} endpoints whose
 * request body shape varies per provider.
 *
 * <p>The protected {@link #toServiceDto} helper normalises the provider-specific request DTO into
 * the {@link IpMappingRequestDto} the service expects.
 *
 * @param <T> the provider-specific request DTO type, must extend {@link BaseIpMappingRequestDto}
 */
@RequestMapping("/api/v1/ip-mappings")
public abstract class BaseIpMappingController<T extends BaseIpMappingRequestDto> {

    protected final IpMappingService service;

    protected BaseIpMappingController(IpMappingService service) {
        this.service = service;
    }

    /**
     * Converts the internal {@link IpMappingResponseDto} into the provider-specific response shape.
     * Called by every read/write endpoint so that subclasses control the response structure in one
     * place.
     *
     * @param internal the internal service response DTO
     * @return the provider-specific {@link BaseIpMappingResponseDto}
     */
    protected abstract BaseIpMappingResponseDto toResponse(IpMappingResponseDto internal);

    /**
     * Normalises a provider-specific request DTO into the {@link IpMappingRequestDto} the service
     * expects. Passes the provider-specific location block through directly, along with the
     * top-level {@code representedCountryGeonameId} and {@code registeredCountryGeonameId} fields.
     *
     * @param source the provider-specific request DTO of type {@code T}
     * @return the normalised {@link IpMappingRequestDto}
     */
    protected abstract IpMappingRequestDto toServiceDto(T source);

    @Operation(
            summary = "Get all ip mappings",
            description = "Get all ip mappings",
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
    public PaginationResponse<BaseIpMappingResponseDto> findAll(
            @ParameterObject @Valid @ModelAttribute PaginationRequest req) {
        PaginationResponse<IpMappingResponseDto> page = service.searchWithPagination(req);
        List<BaseIpMappingResponseDto> mapped =
                page.content().stream().map(this::toResponse).toList();
        return new PaginationResponse<>(
                mapped,
                page.last(),
                page.totalElements(),
                page.totalPages(),
                page.first(),
                page.size(),
                page.number(),
                page.numberOfElements(),
                page.empty());
    }

    @Operation(
            summary = "Find an ip mapping by id",
            description = "Find an ip mapping by id",
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
                                                                BaseIpMappingResponseDto.class))),
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
    @GetMapping("/{id}")
    public BaseIpMappingResponseDto findById(@Valid @PathVariable Long id) {
        return toResponse(service.searchById(id));
    }

    @Operation(
            summary = "Delete an ip mapping",
            description = "Delete an ip mapping by id",
            responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Successful response",
                        content = @Content),
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
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @PathVariable Long id) {
        service.delete(id);
    }

    @Operation(
            summary = "Rebuild IP spans by id range",
            description =
                    "Asynchronously deletes and recreates IP spans for ip_mapping records within"
                            + " the given id range [startId, endId] using max prefix length /24."
                            + " Returns immediately while the rebuild runs in the background."
                            + " If the process is interrupted, resume by calling again with"
                            + " startId set to the last logged cursor id.",
            responses = {
                @ApiResponse(
                        responseCode = "202",
                        description = "Rebuild accepted",
                        content = @Content)
            })
    @PostMapping("/ip-spans/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void rebuildIpSpans(
            @RequestParam(name = "start_id") Long startId,
            @RequestParam(name = "end_id") Long endId) {
        service.rebuildAllIpSpans(startId, endId);
    }
}
