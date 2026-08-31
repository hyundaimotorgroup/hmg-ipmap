package com.hmg.ipmap.location;

import com.hmg.ipmap.cache.dto.LocationNameCacheDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link LocationNameEntity} and {@link
 * com.hmg.ipmap.cache.dto.LocationNameCacheDto}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LocationNameMapper {

    /**
     * Maps a {@link com.hmg.ipmap.cache.dto.LocationNameCacheDto} to a {@link LocationNameEntity},
     * reconstructing the composite primary key from the DTO's location id and locale code.
     *
     * @param locationNameCacheDto the source cache DTO
     * @return the mapped entity
     */
    @Mapping(
            target = "locationNameId",
            expression =
                    "java(new LocationNameId(locationNameCacheDto.getLocationId(), locationNameCacheDto.getLocaleCode()))")
    LocationNameEntity cacheToEntity(LocationNameCacheDto locationNameCacheDto);

    /**
     * Maps a {@link LocationNameEntity} to a {@link com.hmg.ipmap.cache.dto.LocationNameCacheDto}.
     *
     * @param locationNameEntity the source entity
     * @return the mapped cache DTO
     */
    @Mapping(target = "locationId", source = "locationNameId.locationId")
    @Mapping(target = "localeCode", source = "locationNameId.localeCode")
    LocationNameCacheDto entityToCache(LocationNameEntity locationNameEntity);
}
