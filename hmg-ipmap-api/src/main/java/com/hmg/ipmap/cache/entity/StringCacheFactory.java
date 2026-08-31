package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;

public class StringCacheFactory {
    private StringCacheFactory() {}

    public static StringCacheEntity createStringCacheEntity(CacheOperation cacheOperation) {
        return switch (cacheOperation.getAction().cacheTable.getTableName()) {
            case "ip_mapping" -> new IpMappingCacheEntity(cacheOperation);
            case "location" -> new LocationCacheEntity(cacheOperation);
            default -> new GeneralStringCacheEntity(cacheOperation);
        };
    }
}
