package com.hmg.ipmap.cache.event;

import com.hmg.ipmap.common.CachedEntity;
import com.hmg.ipmap.ipmapping.IpMappingAttributeMapper;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingMapper;
import com.hmg.ipmap.ipmapping.IpSpanEntity;
import com.hmg.ipmap.ipmapping.IpSpanMapper;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationMapper;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheEntityConverter {

    private final IpMappingMapper ipMappingMapper;
    private final IpSpanMapper ipSpanMapper;
    private final IpMappingAttributeMapper ipMappingAttributeMapper;
    private final LocationMapper locationMapper;
    private final LocationNameMapper locationNameMapper;

    public CacheEntityConverter(
            IpMappingMapper ipMappingMapper,
            IpSpanMapper ipSpanMapper,
            IpMappingAttributeMapper ipMappingAttributeMapper,
            LocationMapper locationMapper,
            LocationNameMapper locationNameMapper) {
        this.ipMappingMapper = ipMappingMapper;
        this.ipSpanMapper = ipSpanMapper;
        this.ipMappingAttributeMapper = ipMappingAttributeMapper;
        this.locationMapper = locationMapper;
        this.locationNameMapper = locationNameMapper;
    }

    public <T extends CachedEntity> Object convertToCacheDto(T entity) {
        if (entity == null) {
            return null;
        }

        try {
            return switch (entity.tableName()) {
                case "ip_span" -> ipSpanMapper.entityToCache((IpSpanEntity) entity);
                case "ip_mapping" -> ipMappingMapper.entityToCache((IpMappingEntity) entity);
                case "ip_mapping_attribute" ->
                        ipMappingAttributeMapper.entityToCache((IpMappingAttributeEntity) entity);
                case "location" -> locationMapper.entityToCache((LocationEntity) entity);
                case "location_name" ->
                        locationNameMapper.entityToCache((LocationNameEntity) entity);
                default -> {
                    log.warn("Unknown table name: {}", entity.tableName());
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("Failed to convert entity to cache DTO. tableName={}", entity.tableName(), e);
            return null;
        }
    }
}
