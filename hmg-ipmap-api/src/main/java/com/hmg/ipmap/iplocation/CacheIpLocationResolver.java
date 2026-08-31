package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.cache.CacheService;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingMapper;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanEntity;
import com.hmg.ipmap.ipmapping.IpSpanMapper;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationMapper;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Order(1)
@RequiredArgsConstructor
public class CacheIpLocationResolver implements IpLocationResolver {

    private final CacheService cacheService;
    private final IpSpanMapper ipSpanMapper;
    private final IpMappingMapper ipMappingMapper;
    private final LocationMapper locationMapper;
    private final LocationNameMapper locationNameMapper;
    private final IpMappingService ipMappingService;
    private final IpLocationDomainDataAssembler assembler;

    @Override
    public Optional<IpLocationDomainData> resolve(String ip) {
        try {
            return Optional.of(resolveFromCache(ip));
        } catch (NotFoundException _) {
            log.warn("IP location is not found from cache, fallback to next resolver. ip={}", ip);
            return Optional.empty();
        } catch (Exception e) {
            log.error(
                    "Unable to perform IP Lookup from cache, fallback to next resolver. ip={}",
                    ip,
                    e);
            return Optional.empty();
        }
    }

    private IpLocationDomainData resolveFromCache(String ip) {
        IpSpanCacheDto ipSpanCacheDto = cacheService.findIpLocation(ip, UserContextHolder.get());
        if (ipSpanCacheDto == null) {
            throw new NotFoundException("Unable to fetch ip span from cache");
        }

        IpSpanEntity ipSpan = ipSpanMapper.cacheToEntity(ipSpanCacheDto);
        IpMappingResponseDto ipMappingResponseDto;

        if (ipSpanCacheDto.getIpMappingDto() != null) {
            IpMappingEntity ipMapping =
                    ipMappingMapper.cacheToEntity(ipSpanCacheDto.getIpMappingDto());
            ipSpan.setIpMapping(ipMapping);
            LocationCacheDto locationDto = ipSpanCacheDto.getIpMappingDto().getLocationDto();
            if (locationDto != null) {
                LocationEntity locationEntity = locationMapper.cacheToEntity(locationDto);
                List<LocationNameEntity> list =
                        Optional.ofNullable(locationDto.getNameDtos())
                                .orElse(Collections.emptyList())
                                .stream()
                                .map(locationNameMapper::cacheToEntity)
                                .toList();
                locationEntity.setNames(list);
                ipMapping.setLocation(locationEntity);
            }
            ipMappingResponseDto =
                    ipMappingService.getAndSetIpMappingResponseDto(
                            ipSpanCacheDto.getIpMappingDto(), ipMapping);
        } else {
            IpMappingEntity ipMappingEntity =
                    ipMappingService
                            .findByIdWithAttributes(ipSpanCacheDto.getIpMappingId())
                            .orElseThrow(() -> new NotFoundException("Ip Mapping not found"));
            ipSpan.setIpMapping(ipMappingEntity);
            ipMappingResponseDto = ipMappingService.getAndSetIpMappingResponseDto(ipMappingEntity);
        }

        return assembler.assemble(ipMappingResponseDto, ip, ipSpan.getIpMapping().getScope());
    }
}
