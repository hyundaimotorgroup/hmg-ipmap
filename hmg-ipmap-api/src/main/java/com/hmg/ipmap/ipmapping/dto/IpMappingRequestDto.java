package com.hmg.ipmap.ipmapping.dto;

import com.hmg.ipmap.location.dto.BaseLocationRequestDto;
import java.time.Instant;
import java.util.Map;

public record IpMappingRequestDto(
        String ipNotation,
        Instant validPeriod,
        Map<String, Map<String, Object>> attributes,
        BaseLocationRequestDto location,
        Long representedCountryGeonameId,
        Long registeredCountryGeonameId) {}
