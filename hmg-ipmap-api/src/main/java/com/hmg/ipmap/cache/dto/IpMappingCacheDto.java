package com.hmg.ipmap.cache.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.common.enums.Scope;
import com.univocity.parsers.annotations.Parsed;
import java.util.List;
import lombok.Data;

@Data
public class IpMappingCacheDto {

    @Parsed(index = 0)
    private long id;

    @Parsed(index = 1)
    private Long createdAt;

    @Parsed(index = 2)
    private String ipNotation;

    @Parsed(index = 3)
    private String notationType;

    @Parsed(index = 4)
    private String registeredCountryGeonameId;

    @Parsed(index = 5)
    private String representedCountryGeonameId;

    @Parsed(index = 6)
    private Scope scope;

    @Parsed(index = 7)
    private Long updatedAt;

    @Parsed(index = 8)
    private Long validPeriod;

    @Parsed(index = 9)
    private long locationId;

    @Parsed(index = 10)
    private long userId;

    @JsonIgnore private List<IpMappingAttributeCacheDto> attributeDtos;
    @JsonIgnore private LocationCacheDto locationDto;
}
