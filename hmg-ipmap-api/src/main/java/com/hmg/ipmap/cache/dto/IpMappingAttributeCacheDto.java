package com.hmg.ipmap.cache.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmg.ipmap.common.util.MapperUtil;
import com.univocity.parsers.annotations.Parsed;
import java.util.Collections;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Slf4j
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IpMappingAttributeCacheDto {

    @Parsed(index = 0)
    private Long id;

    @Parsed(index = 1)
    private String attributes;

    @Parsed(index = 2)
    private String objectName;

    @Parsed(index = 3)
    private Long ipMappingId;

    @JsonIgnore
    public Map<String, Object> getAttributeMap() {
        try {
            if (attributes == null) return Map.of();
            return MapperUtil.getObjectMapper()
                    .readValue(this.attributes, new TypeReference<>() {});
        } catch (JacksonException e) {
            log.warn("Error parsing IpMappingAttributeCacheDto.attributes", e);
            return Collections.emptyMap();
        }
    }
}
