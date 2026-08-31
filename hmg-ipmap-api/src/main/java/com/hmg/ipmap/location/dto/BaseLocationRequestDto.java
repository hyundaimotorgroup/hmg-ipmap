package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.location.enums.LocationLevel;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abstract base for all location create/update request bodies.
 *
 * <p>Carries the three tiers common to every data-provider variant (continent, country, city).
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseLocationRequestDto {

    @Valid private LocationDto continent;

    @Valid private LocationDto country;

    @Valid private LocationDto city;

    /**
     * Returns the ordered additional locations for this request, from broadest to most specific.
     * Implementations must never return {@code null}; return an empty list when no additional
     * locations are present.
     */
    @JsonIgnore
    public abstract List<LocationDto> getAdditionalLocations();

    @JsonIgnore
    public abstract Map<LocationLevel, LocationDto> getAllLocationMap();
}
