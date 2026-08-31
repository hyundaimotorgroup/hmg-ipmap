package com.hmg.ipmap.cache.event;

import java.time.Instant;

public record CacheUpdateEvent(
        String action, String tableName, Object cacheDto, Instant sourceTimestamp) {}
