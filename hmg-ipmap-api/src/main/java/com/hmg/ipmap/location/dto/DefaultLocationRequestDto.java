package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.location.enums.LocationLevel;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Location request DTO for the {@code default} data-provider. */
@Getter
@Setter
@NoArgsConstructor
public class DefaultLocationRequestDto extends BaseLocationRequestDto {

    @Valid private LocationDto region;

    @Override
    public List<LocationDto> getAdditionalLocations() {
        return region != null ? List.of(region) : List.of();
    }

    @Override
    @JsonIgnore
    public Map<LocationLevel, LocationDto> getAllLocationMap() {
        Map<LocationLevel, LocationDto> map = new LinkedHashMap<>();
        map.put(DefaultLocationLevel.CONTINENT, getContinent());
        map.put(DefaultLocationLevel.COUNTRY, getCountry());
        map.put(DefaultLocationLevel.REGION, getRegion());
        map.put(DefaultLocationLevel.CITY, getCity());
        return map;
    }
}
