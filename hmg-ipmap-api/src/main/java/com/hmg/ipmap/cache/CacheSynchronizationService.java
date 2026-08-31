package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import java.time.Instant;
import java.util.List;

public interface CacheSynchronizationService {
    void syncCache(String action, String tableName, Object cacheDto);

    void syncCache(List<CacheSyncJobEntity> jobs);

    void saveCacheSyncJob(
            String action, String tableName, Object cacheDto, Instant sourceTimestamp);
}
