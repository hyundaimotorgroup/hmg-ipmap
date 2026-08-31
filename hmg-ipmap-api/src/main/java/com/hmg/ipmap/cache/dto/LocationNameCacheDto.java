package com.hmg.ipmap.cache.dto;

import com.univocity.parsers.annotations.Parsed;
import lombok.Data;

@Data
public class LocationNameCacheDto {
    @Parsed(index = 0)
    private String localeCode;

    @Parsed(index = 1)
    private String name;

    @Parsed(index = 2)
    private long locationId;
}
