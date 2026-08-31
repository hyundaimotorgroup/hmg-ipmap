package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.Map;

public abstract class HashCacheEntity {
    protected CacheOperation cacheOp;

    protected HashCacheEntity(CacheOperation cacheOp) {
        this.cacheOp = cacheOp;
    }

    public abstract String getKey();

    public String getField() {
        if (cacheOp.getAction().isLocationName()) {
            return String.format(
                    "%s-%s",
                    cacheOp.getDataString("locale_code"), cacheOp.getDataString("location_id"));
        } else if (cacheOp.getAction().isLocation()) {
            return cacheOp.getDataString("id");
        }
        return cacheOp.getDataString("id");
    }

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
