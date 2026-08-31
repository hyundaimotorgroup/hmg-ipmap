package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.cache.exception.CacheOperationException;
import com.hmg.ipmap.cache.helper.CsvCodec;

public class LocationCacheEntity extends StringCacheEntity {
    public static final String KEY_PATTERN = "location:{loc_id:%d}";

    public LocationCacheEntity(CacheOperation cacheOp) {
        super(cacheOp);
    }

    @Override
    public String getKey() {
        Long locationId = cacheOp.getDataLong("id");
        if (locationId == null) {
            throw new CacheOperationException("Location id is null");
        }
        return String.format(KEY_PATTERN, locationId);
    }

    public static String getKey(Long locationId) {
        return String.format(KEY_PATTERN, locationId);
    }

    @Override
    public String getValue() {
        return CsvCodec.encodeFromMapToCsv(cacheOp.getData(), LocationCacheDto.class);
    }
}
