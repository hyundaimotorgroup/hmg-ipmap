package com.hmg.ipmap.iplocation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.location.dto.LocationDto;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IpMappingDomainData {

    private String ipAddress;

    private Scope scope;

    private Map<String, Map<String, Object>> attributes;

    private LocationDto representedCountry;

    private LocationDto registeredCountry;
}
