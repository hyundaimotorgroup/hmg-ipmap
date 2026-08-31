package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.IpMappingAttributeCacheDto;
import com.hmg.ipmap.cache.exception.CacheOperationException;
import com.hmg.ipmap.cache.helper.CsvCodec;

public class IpMappingAttributeCacheEntity extends HashCacheEntity {
    public static final String KEY_PATTERN = "ip_mapping_attribute:{ip_map:%d}";

    public IpMappingAttributeCacheEntity(CacheOperation cacheOp) {
        super(cacheOp);
    }

    @Override
    public String getKey() {
        Long ipMappingId = cacheOp.getDataLong("ip_mapping_id");
        if (ipMappingId == null) {
            throw new CacheOperationException("ip_mapping_id is null");
        }
        return String.format(KEY_PATTERN, cacheOp.getDataLong("ip_mapping_id"));
    }

    public static String getKey(long ipMappingId) {
        return String.format(KEY_PATTERN, ipMappingId);
    }

    @Override
    public String getValue() {
        return CsvCodec.encodeFromMapToCsv(cacheOp.getData(), IpMappingAttributeCacheDto.class);
    }
}
