package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.dto.IpMappingAttributeCacheDto;
import com.hmg.ipmap.common.util.MapperUtil;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link IpMappingAttributeEntity} and {@link
 * IpMappingAttributeCacheDto}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IpMappingAttributeMapper {
    /**
     * Converts a cache DTO to an attribute entity. The {@code attributes} map is populated by the
     * {@link #setAttribute} after-mapping callback.
     */
    @Mapping(target = "attributes", ignore = true)
    IpMappingAttributeEntity cacheToEntity(IpMappingAttributeCacheDto ipMappingAttributeCacheDto);

    /**
     * Converts an attribute entity to a cache DTO. The {@code attributes} JSON string is populated
     * by the {@link #mapIpMappingAttributesToString} after-mapping callback.
     */
    @Mapping(target = "ipMappingId", source = "ipMapping.id")
    @Mapping(target = "attributes", ignore = true)
    IpMappingAttributeCacheDto entityToCache(IpMappingAttributeEntity ipMappingAttributeEntity);

    /**
     * After-mapping hook that serialises the entity's attribute map to a JSON string on the target
     * cache DTO.
     */
    @AfterMapping
    default void mapIpMappingAttributesToString(
            IpMappingAttributeEntity source, @MappingTarget IpMappingAttributeCacheDto target) {
        Map<String, Object> attributes = source.getAttributes();
        if (attributes != null) {
            target.setAttributes(MapperUtil.toJsonSafe(attributes));
        }
    }

    /**
     * After-mapping hook that deserialises the cache DTO's JSON string back into the entity's
     * attribute map.
     */
    @AfterMapping
    default void setAttribute(
            IpMappingAttributeCacheDto source, @MappingTarget IpMappingAttributeEntity target) {
        target.setAttributes(source.getAttributeMap());
    }
}
