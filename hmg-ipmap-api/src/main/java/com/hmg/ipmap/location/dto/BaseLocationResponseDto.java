package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abstract base for all location response bodies.
 *
 * <p>Carries the three tiers common to every data-provider variant (continent, country, city).
 * Concrete subclasses add their own subdivision field(s) so the API response shape matches the
 * provider schema that was used on the request side.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class BaseLocationResponseDto {

    @Schema(description = "The continent information")
    private LocationDto continent;

    @Schema(description = "The country information")
    private LocationDto country;

    @Schema(description = "The city information")
    private LocationDto city;
}
