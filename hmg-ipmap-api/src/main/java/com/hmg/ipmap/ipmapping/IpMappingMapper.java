package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link IpMappingEntity}, {@link IpMappingRequestDto},
 * {@link IpMappingResponseDto}, and {@link IpMappingCacheDto}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IpMappingMapper {

    /**
     * Converts a request DTO to a new entity. The {@code location} and {@code attributes} fields
     * are left unmapped and must be set separately.
     */
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "attributes", ignore = true)
    IpMappingEntity toEntity(IpMappingRequestDto ipMappingRequestDto);

    /**
     * Converts an entity to a response DTO. The {@code attributes} field is left unmapped and must
     * be populated separately via the attribute service.
     */
    @Mapping(target = "attributes", ignore = true)
    IpMappingResponseDto toDto(IpMappingEntity ipMapping);

    /**
     * Converts a request DTO directly to a response DTO. Location is set manually by the caller.
     */
    @Mapping(target = "location", ignore = true)
    IpMappingResponseDto reqDtoToRespDto(IpMappingRequestDto ipMappingRequestDto);

    /** Converts a cache DTO back to an entity. */
    IpMappingEntity cacheToEntity(IpMappingCacheDto ipMappingCacheDto);

    /**
     * Converts an entity to a cache DTO, deriving {@code locationId} from the entity's {@code
     * location} association and {@code userId} from the entity's {@code user} association.
     */
    @Mapping(target = "locationId", source = "location.id")
    @Mapping(target = "userId", source = "user.id")
    IpMappingCacheDto entityToCache(IpMappingEntity ipMappingEntity);

    /**
     * Converts an epoch-millisecond timestamp to an {@link Instant}, or {@code null} if the input
     * is {@code null}.
     */
    default Instant map(Long timestamp) {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : null;
    }

    /**
     * Converts an {@link Instant} to epoch milliseconds, or {@code null} if the input is {@code
     * null}.
     */
    default Long map(Instant value) {
        return (value == null) ? null : value.toEpochMilli();
    }
}
