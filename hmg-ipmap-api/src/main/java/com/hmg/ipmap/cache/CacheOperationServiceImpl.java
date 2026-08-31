package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.CacheOperationError;
import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;
import com.hmg.ipmap.cache.entity.HashCacheEntity;
import com.hmg.ipmap.cache.entity.HashCacheFactory;
import com.hmg.ipmap.cache.entity.IpSpanSortedSetCacheEntity;
import com.hmg.ipmap.cache.entity.SortedSetCacheEntity;
import com.hmg.ipmap.cache.entity.StringCacheEntity;
import com.hmg.ipmap.cache.entity.StringCacheFactory;
import com.hmg.ipmap.cache.exception.CacheOperationException;
import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.util.IPv4Util;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CacheOperationServiceImpl implements CacheOperationService {
    private static final String ERROR_DELETE_NOT_FOUND = "Failed to delete. Data not found";
    private static final String ERROR_UPDATE_FAILED = "update failed";
    private static final String LOG_HASH_DELETE_FAILED = "{} opsForHash.delete failed";
    private static final String LOG_HASH_PUT_FAILED = "{} .opsForHash.put failed";
    private static final String LOG_ZSET_REMOVE_FAILED = "{} opsForZSet.remove failed";
    private static final String LOG_ZSET_ADD_FAILED = "{} opsForZSet.add failed";
    private static final String LOG_DELETE_FAILED = "{} delete failed";
    private static final String LOG_VALUE_PUT_FAILED = "{} .opsForValue.put failed";
    private static final String LOG_CACHE_PUT_ERROR_WITH_FIELD =
            "Error while putting data to cache. key={} field={}";
    private static final String LOG_CACHE_PUT_ERROR = "Error while putting data to cache. key={}";

    private final RedisTemplate<String, String> redisTemplate;
    private final CacheOperationValidator cacheOperationValidator;
    private final IpNotationFactory ipNotationFactory;
    private final IpSpanProperties ipSpanProperties;

    public CacheOperationServiceImpl(
            @Qualifier("redisCacheTemplate") RedisTemplate<String, String> redisTemplate,
            CacheOperationValidator cacheOperationValidator,
            IpNotationFactory ipNotationFactory,
            IpSpanProperties ipSpanProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheOperationValidator = cacheOperationValidator;
        this.ipNotationFactory = ipNotationFactory;
        this.ipSpanProperties = ipSpanProperties;
    }

    @Override
    public CacheOperationResponseDto updateCache(
            CacheOperationRequestDto cacheOperationRequestDto) {
        // validate
        List<CacheOperationError> errors =
                cacheOperationValidator.validate(cacheOperationRequestDto);
        // categorize the events
        Map<String, List<SortedSetCacheEntity>> sortedSetMap = new HashMap<>();
        Map<String, List<HashCacheEntity>> hashSetMap = new HashMap<>();
        Map<String, List<StringCacheEntity>> stringMap = new HashMap<>();

        for (CacheOperation cacheOps : cacheOperationRequestDto.getOperations()) {
            if (cacheOps.getAction().isSortedSet()) {
                // sorted set
                try {
                    Long ipLower = cacheOps.getDataLong("ip_lower");
                    String subnet =
                            ipLower != null
                                    ? ipNotationFactory.mapIpToSubnet(
                                            IPv4Util.longToIpString(ipLower),
                                            ipSpanProperties.getSubnetPrefixLength())
                                    : null;
                    SortedSetCacheEntity sortedSetCache =
                            IpSpanSortedSetCacheEntity.ofCacheOperationRequest(cacheOps, subnet);
                    sortedSetMap.putIfAbsent(sortedSetCache.getKey(), new ArrayList<>());
                    sortedSetMap.get(sortedSetCache.getKey()).add(sortedSetCache);
                } catch (CacheOperationException e) {
                    errors.add(
                            CacheOperationError.builder()
                                    .action(cacheOps.getAction())
                                    .data(cacheOps.getData())
                                    .errorMessage(e.getMessage())
                                    .build());
                    log.error(
                            "Unable to parse cache operation. tableName={} data={}",
                            cacheOps.getAction().cacheTable.toString(),
                            cacheOps.getData(),
                            e);
                }
            } else if (cacheOps.getAction().isString()) {
                // string type
                StringCacheEntity stringCacheEntity =
                        StringCacheFactory.createStringCacheEntity(cacheOps);
                stringMap.putIfAbsent(stringCacheEntity.getKey(), new ArrayList<>());
                stringMap.get(stringCacheEntity.getKey()).add(stringCacheEntity);
            } else {
                // hash set
                HashCacheEntity hashCacheEntity = HashCacheFactory.createHashCacheEntity(cacheOps);
                hashSetMap.putIfAbsent(hashCacheEntity.getKey(), new ArrayList<>());
                hashSetMap.get(hashCacheEntity.getKey()).add(hashCacheEntity);
            }
        }
        sortedSetMap.forEach((key, value) -> updateSortedSetCache(value, errors));
        hashSetMap.forEach((key, value) -> updateHashSetCache(value, errors));
        stringMap.forEach((key, value) -> updateStringCache(value, errors));

        int successCount = cacheOperationRequestDto.getOperations().size() - errors.size();

        log.info("Cache Sync Complete with :");
        log.info("Success {}, failed {}", successCount, errors.size());

        return CacheOperationResponseDto.builder()
                .successCount(successCount)
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    private void updateHashSetCache(
            List<HashCacheEntity> hashCacheEntities, List<CacheOperationError> errors) {
        hashCacheEntities.forEach(
                hashCacheEntity -> {
                    if ('d' == hashCacheEntity.getCacheOpsAction().operation) {
                        Long evict = evictHashCache(hashCacheEntity, redisTemplate);
                        if (evict < 1) {
                            log.warn(
                                    LOG_HASH_DELETE_FAILED,
                                    hashCacheEntity.getSourceData().toString());
                            putError(hashCacheEntity, ERROR_DELETE_NOT_FOUND, errors);
                        }
                    } else {
                        boolean status = putHashCache(hashCacheEntity, redisTemplate);
                        if (!status) {
                            log.warn(
                                    LOG_HASH_PUT_FAILED,
                                    hashCacheEntity.getSourceData().toString());
                            putError(hashCacheEntity, ERROR_UPDATE_FAILED, errors);
                        }
                    }
                });
    }

    private void updateSortedSetCache(
            List<SortedSetCacheEntity> sortedSetCacheEntities, List<CacheOperationError> errors) {
        sortedSetCacheEntities.forEach(
                cacheEntity -> {
                    if ('d' == cacheEntity.getCacheOpsAction().operation) {
                        Long evict = evictSortedSetCache(cacheEntity, redisTemplate);
                        if (evict == null) {
                            log.warn(
                                    LOG_ZSET_REMOVE_FAILED, cacheEntity.getSourceData().toString());
                            putError(cacheEntity, ERROR_DELETE_NOT_FOUND, errors);
                        }
                    } else {
                        Boolean status = putSortedSetCache(cacheEntity, redisTemplate);
                        if (Boolean.FALSE.equals(status)) {
                            log.warn(LOG_ZSET_ADD_FAILED, cacheEntity.getSourceData().toString());
                            putError(cacheEntity, ERROR_UPDATE_FAILED, errors);
                        }
                    }
                });
    }

    private void updateStringCache(
            List<StringCacheEntity> stringCacheEntities, List<CacheOperationError> errors) {
        stringCacheEntities.forEach(
                stringCacheEntity -> {
                    if ('d' == stringCacheEntity.getCacheOpsAction().operation) {
                        boolean evict = evictStringCache(stringCacheEntity, redisTemplate);
                        if (!evict) {
                            log.warn(
                                    LOG_DELETE_FAILED,
                                    stringCacheEntity.getSourceData().toString());
                            putError(stringCacheEntity, ERROR_DELETE_NOT_FOUND, errors);
                        }
                    } else {
                        boolean status = putStringCache(stringCacheEntity, redisTemplate);
                        if (!status) {
                            log.warn(
                                    LOG_VALUE_PUT_FAILED,
                                    stringCacheEntity.getSourceData().toString());
                            putError(stringCacheEntity, ERROR_UPDATE_FAILED, errors);
                        }
                    }
                });
    }

    private boolean putHashCache(
            HashCacheEntity cacheEntity, RedisOperations<String, String> operations) {
        boolean status = false;
        try {
            if (cacheEntity.getValue() != null) {
                operations
                        .opsForHash()
                        .put(cacheEntity.getKey(), cacheEntity.getField(), cacheEntity.getValue());
                status = true;
            }
        } catch (Exception e) {
            // Catching generic Exception to handle Redis connection issues,
            // serialization errors, and other runtime exceptions during cache operations
            log.error(
                    LOG_CACHE_PUT_ERROR_WITH_FIELD,
                    cacheEntity.getKey(),
                    cacheEntity.getField(),
                    e);
        }
        return status;
    }

    private Long evictHashCache(
            HashCacheEntity cacheEntity, RedisOperations<String, String> operations) {
        return operations.opsForHash().delete(cacheEntity.getKey(), cacheEntity.getField());
    }

    private Boolean putSortedSetCache(
            SortedSetCacheEntity sortedSetCache, RedisOperations<String, String> operations) {
        return operations
                .opsForZSet()
                .add(
                        sortedSetCache.getKey(),
                        sortedSetCache.getMember(),
                        sortedSetCache.getScore());
    }

    private Long evictSortedSetCache(
            SortedSetCacheEntity sortedSetCache, RedisOperations<String, String> operations) {
        return operations.opsForZSet().remove(sortedSetCache.getKey(), sortedSetCache.getMember());
    }

    private boolean putStringCache(
            StringCacheEntity cacheEntity, RedisOperations<String, String> operations) {
        boolean status = false;
        try {
            if (cacheEntity.getValue() != null) {
                operations.opsForValue().set(cacheEntity.getKey(), cacheEntity.getValue());
                status = true;
            }
        } catch (Exception e) {
            // Catching generic Exception to handle Redis connection issues,
            // serialization errors, and other runtime exceptions during cache operations
            log.error(LOG_CACHE_PUT_ERROR, cacheEntity.getKey(), e);
        }
        return status;
    }

    private Boolean evictStringCache(
            StringCacheEntity cacheEntity, RedisOperations<String, String> operations) {
        return operations.delete(cacheEntity.getKey());
    }

    private void putError(
            SortedSetCacheEntity sortedSetCache, String message, List<CacheOperationError> errors) {
        errors.add(
                new CacheOperationError(
                        sortedSetCache.getSourceData(),
                        message,
                        sortedSetCache.getCacheOpsAction()));
    }

    private void putError(
            HashCacheEntity hashCacheEntity, String message, List<CacheOperationError> errors) {
        errors.add(
                new CacheOperationError(
                        hashCacheEntity.getSourceData(),
                        message,
                        hashCacheEntity.getCacheOpsAction()));
    }

    private void putError(
            StringCacheEntity stringCacheEntity, String message, List<CacheOperationError> errors) {
        errors.add(
                new CacheOperationError(
                        stringCacheEntity.getSourceData(),
                        message,
                        stringCacheEntity.getCacheOpsAction()));
    }
}
