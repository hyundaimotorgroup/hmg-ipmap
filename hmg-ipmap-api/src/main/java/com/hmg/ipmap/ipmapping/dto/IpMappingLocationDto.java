package com.hmg.ipmap.ipmapping.dto;

import com.hmg.ipmap.location.dto.LocationDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpMappingLocationDto {
    private @Valid LocationDto continent;

    private @Valid LocationDto country;

    private @Valid List<LocationDto> additionalLocations;

    private @Valid LocationDto city;
}
