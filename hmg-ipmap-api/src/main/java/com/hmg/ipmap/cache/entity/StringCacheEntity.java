package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.Map;

public abstract class StringCacheEntity {
    protected CacheOperation cacheOp;

    protected StringCacheEntity(CacheOperation cacheOp) {
        this.cacheOp = cacheOp;
    }

    public abstract String getKey();

    public String getValue() {
        return cacheOp.getJsonDataString();
    }

    public CacheOpsAction getCacheOpsAction() {
        return cacheOp.getAction();
    }

    public Map<String, String> getSourceData() {
        return cacheOp.getData();
    }
}
