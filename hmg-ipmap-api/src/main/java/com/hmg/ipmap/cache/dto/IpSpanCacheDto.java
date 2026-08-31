package com.hmg.ipmap.cache.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.common.enums.Scope;
import com.univocity.parsers.annotations.Parsed;
import lombok.Data;

@Data
public class IpSpanCacheDto {

    private long id;

    private long ipLower;

    @Parsed(index = 0)
    private long ipUpper;

    @Parsed(index = 1)
    private long ipMappingId;

    @Parsed(index = 2)
    private Scope scope;

    @Parsed(index = 3)
    private long createdAt;

    @Parsed(index = 4)
    private long userId;

    @Parsed(index = 5)
    private Long validPeriod;

    @JsonIgnore private IpMappingCacheDto ipMappingDto;
}
