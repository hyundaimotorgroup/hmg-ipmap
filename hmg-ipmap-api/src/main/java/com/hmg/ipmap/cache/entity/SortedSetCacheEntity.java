package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.Map;
import lombok.Getter;

@Getter
public abstract class SortedSetCacheEntity {
    protected String key;
    protected double score;
    protected String member;
    protected CacheOpsAction cacheOpsAction;
    protected Map<String, String> sourceData;
}
