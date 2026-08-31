package com.hmg.ipmap.iplocation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmg.ipmap.location.dto.LocationDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocationDomainData {

    private LocationDto continent;

    private LocationDto country;

    private List<LocationDto> additionalLocations;

    private LocationDto city;
}
