package com.hmg.ipmap.ipmapping.dto;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.location.dto.LocationDto;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpMappingResponseDto {
    private Long id;
    private Scope scope;
    private String ipNotation;
    private Instant validPeriod;
    private Map<String, Map<String, Object>> attributes;
    private Long representedCountryGeonameId;
    private Long registeredCountryGeonameId;
    private LocationDto representedCountry;
    private LocationDto registeredCountry;
    private IpMappingLocationDto location;
}
