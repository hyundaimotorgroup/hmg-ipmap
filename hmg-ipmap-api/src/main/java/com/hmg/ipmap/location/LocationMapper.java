package com.hmg.ipmap.location;

import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.common.util.MapperUtil;
import com.hmg.ipmap.location.dto.LocationDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link LocationEntity} and its DTO/cache representations.
 *
 * <p>Handles conversion of JSONB attributes to JSON strings for cache storage and builds
 * locale-to-name maps from {@link LocationNameEntity} collections via {@code @AfterMapping} hooks.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LocationMapper {

    /**
     * Maps a {@link LocationDto} to a new {@link LocationEntity}; the names collection is not
     * mapped and must be set separately.
     *
     * @param locationDto the source DTO
     * @return the mapped entity
     */
    @Mapping(target = "names", ignore = true)
    LocationEntity dtoToEntity(LocationDto locationDto);

    /**
     * Maps a {@link LocationEntity} to a {@link LocationDto}, populating the names map via {@link
     * #fillNamesMap}.
     *
     * @param location the source entity
     * @return the mapped DTO
     */
    @Mapping(target = "names", ignore = true)
    LocationDto toLocationDto(LocationEntity location);

    /**
     * Maps a {@link LocationCacheDto} to a {@link LocationEntity}; the attributes field is
     * populated separately via the {@link #mapAttributesToString} hook.
     *
     * @param locationCacheDto the source cache DTO
     * @return the mapped entity (without attributes)
     */
    @Mapping(target = "attributes", ignore = true)
    LocationEntity cacheToEntity(LocationCacheDto locationCacheDto);

    /**
     * Maps a {@link LocationEntity} to a {@link LocationCacheDto} for cache storage.
     *
     * @param locationEntity the source entity
     * @return the mapped cache DTO
     */
    @Mapping(target = "attributes", ignore = true)
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "parentId", source = "parent.id")
    LocationCacheDto entityToCache(LocationEntity locationEntity);

    /**
     * Post-mapping hook that converts the entity's name collection into a locale-to-display-name
     * map on the target DTO.
     *
     * @param source the source entity providing the name collection
     * @param target the target DTO whose names map will be populated
     */
    @AfterMapping
    default void fillNamesMap(LocationEntity source, @MappingTarget LocationDto target) {
        Map<String, String> namesMap = buildNamesMap(source.getNames());
        target.setNames(namesMap);
    }

    /**
     * Post-mapping hook that serialises the entity's JSONB attributes map to a JSON string for the
     * cache DTO.
     *
     * @param source the source entity providing the attributes map
     * @param target the target cache DTO whose attributes string will be set
     */
    @AfterMapping
    default void mapAttributesToString(
            LocationEntity source, @MappingTarget LocationCacheDto target) {
        Map<String, Object> attributes = source.getAttributes();
        if (attributes != null) {
            target.setAttributes(MapperUtil.toJsonSafe(attributes));
        }
    }

    /**
     * Converts a list of {@link LocationNameEntity} rows into a locale-to-name map, preserving
     * insertion order and skipping entries with null keys or values.
     *
     * @param names the name entities to convert; may be {@code null} or empty
     * @return an ordered map from locale code to display name; empty map if input is empty
     */
    default Map<String, String> buildNamesMap(List<LocationNameEntity> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (LocationNameEntity n : names) {
            String key = n.getLocationNameId().getLocaleCode();
            String value = n.getName();
            if (key != null && value != null) {
                map.putIfAbsent(key, value); // keeps first occurrence
            }
        }
        return map;
    }
}
