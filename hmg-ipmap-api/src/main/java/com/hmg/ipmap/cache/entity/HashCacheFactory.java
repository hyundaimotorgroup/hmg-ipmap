package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;

public class HashCacheFactory {
    private HashCacheFactory() {}

    public static HashCacheEntity createHashCacheEntity(CacheOperation cacheOperation) {
        return switch (cacheOperation.getAction().cacheTable.getTableName()) {
            case "ip_mapping_attribute" -> new IpMappingAttributeCacheEntity(cacheOperation);
            case "location_name" -> new LocationNameCacheEntity(cacheOperation);
            default -> new GeneralHashCacheEntity(cacheOperation);
        };
    }
}
