package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;

public class GeneralHashCacheEntity extends HashCacheEntity {
    public GeneralHashCacheEntity(CacheOperation event) {
        super(event);
    }

    @Override
    public String getKey() {
        return cacheOp.getAction().cacheTable.getTableName();
    }
}
