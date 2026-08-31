package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.cache.exception.CacheOperationException;
import com.hmg.ipmap.cache.helper.CsvCodec;

public class IpMappingCacheEntity extends StringCacheEntity {
    public static final String KEY_PATTERN = "ip_mapping:{ip_map:%d}";

    public IpMappingCacheEntity(CacheOperation cacheOps) {
        super(cacheOps);
    }

    @Override
    public String getKey() {
        Long ipMappingId = cacheOp.getDataLong("id");
        if (ipMappingId == null) {
            throw new CacheOperationException("Ip mapping id is null");
        }
        return String.format(KEY_PATTERN, ipMappingId);
    }

    public static String getKey(long ipMappingId) {
        return String.format(KEY_PATTERN, ipMappingId);
    }

    @Override
    public String getValue() {
        return CsvCodec.encodeFromMapToCsv(cacheOp.getData(), IpMappingCacheDto.class);
    }
}
