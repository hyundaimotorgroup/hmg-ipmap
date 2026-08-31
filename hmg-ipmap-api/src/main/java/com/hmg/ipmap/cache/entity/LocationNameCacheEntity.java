package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.LocationNameCacheDto;
import com.hmg.ipmap.cache.helper.CsvCodec;

public class LocationNameCacheEntity extends HashCacheEntity {
    public static final String KEY_PATTERN = "location_name:{loc_id:%d}";

    public LocationNameCacheEntity(CacheOperation cacheOp) {
        super(cacheOp);
    }

    @Override
    public String getKey() {
        return String.format(KEY_PATTERN, cacheOp.getDataLong("location_id"));
    }

    public static String getKey(Long locationId) {
        if (locationId == null) {
            throw new IllegalArgumentException(
                    "locationId must not be null for cache key generation");
        }
        return String.format(KEY_PATTERN, locationId);
    }

    @Override
    public String getValue() {
        return CsvCodec.encodeFromMapToCsv(cacheOp.getData(), LocationNameCacheDto.class);
    }
}
