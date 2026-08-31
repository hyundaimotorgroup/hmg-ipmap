package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** MapStruct mapper for converting between {@link IpSpanEntity} and {@link IpSpanCacheDto}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IpSpanMapper {
    /** Converts a cache DTO to an IP span entity. */
    IpSpanEntity cacheToEntity(IpSpanCacheDto ipSpanCacheDto);

    /**
     * Converts an IP span entity to a cache DTO, deriving {@code ipMappingId} from the entity's
     * {@code ipMapping} association.
     */
    @Mapping(target = "ipMappingId", source = "ipMapping.id")
    IpSpanCacheDto entityToCache(IpSpanEntity ipSpanEntity);

    /**
     * Converts an epoch-millisecond timestamp to an {@link Instant}, or {@code null} if the input
     * is {@code null}.
     */
    default Instant mapLongToInstant(Long timestamp) {
        return timestamp != null ? java.time.Instant.ofEpochMilli(timestamp) : null;
    }

    /**
     * Converts an {@link Instant} to epoch milliseconds, or {@code null} if the input is {@code
     * null}.
     */
    default Long mapInstantToLong(Instant timestamp) {
        return timestamp != null ? timestamp.toEpochMilli() : null;
    }
}
