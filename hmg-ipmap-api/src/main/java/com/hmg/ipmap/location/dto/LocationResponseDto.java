package com.hmg.ipmap.location.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Response DTO representing the full geographic hierarchy for a location.
 *
 * <p>Groups the resolved continent, country, additionalLocations, and city into a single flat
 * response object. Fields that are absent in the stored hierarchy are omitted from serialisation
 * via {@code @JsonInclude(NON_EMPTY)}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LocationResponseDto {
    private LocationDto continent;

    private LocationDto country;

    private List<LocationDto> additionalLocations;

    private LocationDto city;
}
