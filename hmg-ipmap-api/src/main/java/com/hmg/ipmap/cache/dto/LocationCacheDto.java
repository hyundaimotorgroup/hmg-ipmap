package com.hmg.ipmap.cache.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.util.MapperUtil;
import com.univocity.parsers.annotations.Parsed;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

@Slf4j
@Data
public class LocationCacheDto {

    @Parsed(index = 0)
    private long id;

    @Parsed(index = 1)
    private String attributes;

    @Parsed(index = 2)
    private Long geonameId;

    @Parsed(index = 3)
    private String locationCode;

    @Parsed(index = 4)
    private String locationLevel;

    @Parsed(index = 5)
    private Long parentId;

    @Parsed(index = 6)
    private long userId;

    @Parsed(index = 7)
    private Scope scope;

    @JsonIgnore private List<LocationNameCacheDto> nameDtos;
    @JsonIgnore private LocationCacheDto parentDto;

    @JsonIgnore
    public Map<String, Object> getAttributeMap() {
        try {
            if (attributes == null) return Map.of();
            return MapperUtil.getObjectMapper()
                    .readValue(this.attributes, new TypeReference<>() {});
        } catch (JacksonException e) {
            log.error("Error parsing LocationCacheDto.attributes", e);
            return Collections.emptyMap();
        }
    }
}
