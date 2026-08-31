package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.IpMappingAttributeCacheDto;
import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.cache.dto.LocationNameCacheDto;
import com.hmg.ipmap.cache.entity.IpMappingAttributeCacheEntity;
import com.hmg.ipmap.cache.entity.IpMappingCacheEntity;
import com.hmg.ipmap.cache.entity.IpSpanSortedSetCacheEntity;
import com.hmg.ipmap.cache.entity.LocationCacheEntity;
import com.hmg.ipmap.cache.entity.LocationNameCacheEntity;
import com.hmg.ipmap.cache.helper.CacheDtoParser;
import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.util.IPv4Util;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CacheServiceImpl implements CacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final IpNotationFactory ipNotationFactory;
    private final IpSpanProperties ipSpanProperties;

    @Value("${cache.fetch.pagination.size:1000}")
    private long paginationSize;

    @Value("${constant.admin.id}")
    private Long adminId;

    public CacheServiceImpl(
            @Qualifier("redisCacheTemplate") RedisTemplate<String, String> redisTemplate,
            IpNotationFactory ipNotationFactory,
            IpSpanProperties ipSpanProperties) {
        this.redisTemplate = redisTemplate;
        this.ipNotationFactory = ipNotationFactory;
        this.ipSpanProperties = ipSpanProperties;
    }

    @Override
    public IpSpanCacheDto findIpLocation(String ip, UserContext user) {
        long score = IPv4Util.ipv4ToLong(ip);
        String subnet =
                ipNotationFactory.mapIpToSubnet(ip, ipSpanProperties.getSubnetPrefixLength());
        String collectionKey = IpSpanSortedSetCacheEntity.buildCollectionKey(subnet);

        // build IpSpanCacheDto
        IpSpanCacheDto ipSpanCacheDto = null;
        Set<String> ipSpans = fetchIpSpanFromCache(collectionKey, score);
        if (CollectionUtils.isNotEmpty(ipSpans)) {
            log.debug(
                    "Found from cache for ip={} collectionKey={} totalSize={}",
                    ip,
                    collectionKey,
                    ipSpans.size());
            long currentTime = Instant.now().toEpochMilli();
            Stream<Long> hierarchyIds =
                    Stream.of(user, user.parent()).filter(Objects::nonNull).map(UserContext::id);

            Set<Long> userIds =
                    Stream.concat(hierarchyIds, Stream.of(adminId)).collect(Collectors.toSet());
            // pre-filter raw CSV strings before parsing to avoid allocating DTOs for misses
            Map<Scope, List<IpSpanCacheDto>> ipSpanMap =
                    ipSpans.stream()
                            .filter(csv -> matchesRawIpSpanFilter(csv, score, userIds, currentTime))
                            .map(CacheDtoParser::parseIpSpan)
                            .filter(Objects::nonNull)
                            .collect(Collectors.groupingBy(IpSpanCacheDto::getScope));
            ipSpanCacheDto =
                    Stream.of(Scope.SUB_CLIENT, Scope.CLIENT, Scope.GLOBAL)
                            .filter(ipSpanMap::containsKey)
                            .map(ipSpanMap::get)
                            .findFirst()
                            .orElse(Collections.emptyList())
                            .stream()
                            .max(Comparator.comparing(IpSpanCacheDto::getCreatedAt))
                            .orElse(null);
        }

        if (ipSpanCacheDto == null) {
            log.warn("Unable to find ip location from cache for ip={}", ip);
            return null;
        }

        // build IpMappingDto
        IpMappingCacheDto ipMappingCacheDto =
                buildIpMappingCacheDto(ipSpanCacheDto.getIpMappingId());
        if (ipMappingCacheDto == null) {
            log.warn("Unable to parse ipMapping from cache. ipSpanCache={} ", ipSpanCacheDto);
            return ipSpanCacheDto;
        }
        ipSpanCacheDto.setIpMappingDto(ipMappingCacheDto);
        ipMappingCacheDto.setLocationDto(buildLocationCacheDto(ipMappingCacheDto.getLocationId()));
        return ipSpanCacheDto;
    }

    /**
     * Checks IpSpan conditions on the raw CSV string to avoid allocating a full DTO for every
     * entry. IpSpan CSV layout: 0=ipUpper, 1=ipMappingId, 2=scope, 3=createdAt, 4=userId,
     * 5=validPeriod (nullable).
     */
    private static boolean matchesRawIpSpanFilter(
            String csv, long score, Set<Long> userIds, long currentTime) {
        String[] t = csv.split(",", -1);
        if (t.length < 6) return false;

        try {
            // condition 1: score <= ipUpper (index 0)
            if (score > Long.parseLong(t[0])) return false;

            // condition 2: userId (index 4) must be in userIds
            if (!userIds.contains(Long.parseLong(t[4]))) return false;

            // condition 3: validPeriod (index 5) null/empty means no expiry
            String validPeriod = t[5];
            if (!validPeriod.isEmpty() && Long.parseLong(validPeriod) < currentTime) return false;

        } catch (NumberFormatException _) {
            return false;
        }

        return true;
    }

    private Set<String> fetchIpSpanFromCache(String key, long score) {
        log.debug(
                "Fetching ip span from cache for key={} score={} with pagination size={} (size<=0 means no pagination)",
                key,
                score,
                paginationSize);

        if (paginationSize <= 0) {
            log.debug(
                    "Fetching ip span from cache without pagination for key={} score={}",
                    key,
                    score);
            return redisTemplate.opsForZSet().reverseRangeByScore(key, 0, score);
        }

        Set<String> allResults = new HashSet<>();
        long currentOffset = 0;
        Set<String> pageResults;

        do {
            log.debug(
                    "Fetching page from cache: key={} offset={} size={}",
                    key,
                    currentOffset,
                    paginationSize);
            pageResults =
                    redisTemplate
                            .opsForZSet()
                            .reverseRangeByScore(key, 0, score, currentOffset, paginationSize);

            if (pageResults != null && !pageResults.isEmpty()) {
                allResults.addAll(pageResults);
                currentOffset += paginationSize;
                log.debug(
                        "Fetched {} records, total so far: {}",
                        pageResults.size(),
                        allResults.size());
            }
        } while (pageResults != null
                && !pageResults.isEmpty()
                && pageResults.size() == paginationSize);

        log.debug(
                "Completed fetching from cache for key={} score={}, total records={}",
                key,
                score,
                allResults.size());
        return allResults;
    }

    private IpMappingCacheDto buildIpMappingCacheDto(long ipMappingId) {
        String ipMappingJson =
                redisTemplate.opsForValue().get(IpMappingCacheEntity.getKey(ipMappingId));

        if (StringUtils.isNotBlank(ipMappingJson)) {
            IpMappingCacheDto ipMappingCache;
            try {
                ipMappingCache = CacheDtoParser.parseIpMapping(ipMappingJson);
            } catch (Exception e) {
                log.error(
                        "Unable to parse ip_mapping data from cache. ipMappingId={} result={}",
                        ipMappingId,
                        ipMappingJson,
                        e);
                return null;
            }
            assert ipMappingCache != null;
            ipMappingCache.setAttributeDtos(buildIpMappingAttributesDto(ipMappingId));
            return ipMappingCache;
        }
        return null;
    }

    private List<IpMappingAttributeCacheDto> buildIpMappingAttributesDto(long ipMappingId) {
        // get ip mapping attributes
        List<Object> ipMappingAttributesJson =
                redisTemplate
                        .opsForHash()
                        .values(IpMappingAttributeCacheEntity.getKey(ipMappingId));
        List<IpMappingAttributeCacheDto> dtos = new ArrayList<>();
        for (Object ipMappingAttributeJson : ipMappingAttributesJson) {
            String json = (String) ipMappingAttributeJson;
            if (StringUtils.isNotBlank(json)) {
                try {
                    IpMappingAttributeCacheDto dto = CacheDtoParser.parseIpMappingAttribute(json);
                    dtos.add(dto);
                } catch (Exception _) {
                    log.warn(
                            "Unable to parse ip_mapping_attribute data from cache. result={}",
                            json);
                }
            } else {
                log.warn(
                        "Unable to get ip_mapping_attribute data from cache. ipMappingId={} result={}",
                        ipMappingId,
                        json);
            }
        }
        return dtos;
    }

    private LocationCacheDto buildLocationCacheDto(Long locationId) {
        // get location
        String locationJson =
                redisTemplate.opsForValue().get(LocationCacheEntity.getKey(locationId));
        if (StringUtils.isNotBlank(locationJson)) {
            LocationCacheDto locationCacheDto;
            try {
                locationCacheDto = CacheDtoParser.parseLocation(locationJson);
            } catch (Exception _) {
                log.warn(
                        "Unable to parse location data from cache. locationId={} result={}",
                        locationId,
                        locationJson);
                return null;
            }

            // get location names
            assert locationCacheDto != null;
            locationCacheDto.setNameDtos(buildLocationNameCacheDto(locationCacheDto.getId()));
            if (locationCacheDto.getParentId() != null) {
                // build parent
                locationCacheDto.setParentDto(
                        buildLocationCacheDto(locationCacheDto.getParentId()));
            }
            return locationCacheDto;
        }
        log.warn("Unable to get location data from cache. locationId={}", locationId);
        return null;
    }

    private List<LocationNameCacheDto> buildLocationNameCacheDto(Long locationId) {
        // get location names
        List<Object> locationNamesJson =
                redisTemplate.opsForHash().values(LocationNameCacheEntity.getKey(locationId));
        List<LocationNameCacheDto> dtos = new ArrayList<>();
        for (Object locationNameJson : locationNamesJson) {
            String json = (String) locationNameJson;
            if (StringUtils.isNotBlank(json)) {
                try {
                    dtos.add(CacheDtoParser.parseLocationName(json));
                } catch (Exception e) {
                    log.error(
                            "Unable to parse location_name data from cache. locationId={} result={}",
                            locationId,
                            json,
                            e);
                }
            } else {
                log.warn("Unable to get location_name data from cache. locationId={}", locationId);
            }
        }
        return dtos;
    }
}
