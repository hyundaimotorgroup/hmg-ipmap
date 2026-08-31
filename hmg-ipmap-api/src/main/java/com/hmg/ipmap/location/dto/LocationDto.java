package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single geographic location node.
 *
 * <p>Used for both request and response payloads at a single hierarchy level (continent, country,
 * subdivision, or city). Carries the optional database id, location code, GeoNames id, free-form
 * attributes, and locale-keyed display names.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

    @Schema(description = "Unique identifier for the location", example = "1")
    @Positive(message = "id must be a positive number")
    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long id;

    @Schema(description = "The location code", example = "US")
    @Size(max = 64, message = "location_code length must be <= {max} characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]*$",
            message = "location_code must be alphanumeric (._- allowed)")
    private String locationCode;

    @Schema(description = "The location geoname id", example = "123123")
    @Positive(message = "geoname_id must be a positive number")
    @NotNull(message = "geoname ID cannot be null")
    private Long geonameId;

    @Schema(
            description = "The location attributes",
            implementation = Map.class,
            example = "{ \"is_in_european_union\": false}")
    private Map<String, Object> attributes = Collections.emptyMap();

    @Schema(
            description = "The location names (locale → display name)",
            implementation = Map.class,
            example = "{ \"en\": \"South Korea\", \"de\": \"Republik Korea\" }")
    @Nullable
    private Map<
                    String,
                    @NotBlank(message = "names value must not be blank")
                    @Size(max = 200, message = "names value must be <= {max} characters") String>
            names = new LinkedHashMap<>();
}
