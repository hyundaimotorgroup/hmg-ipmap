package com.hmg.ipmap.cache.scheduler;

import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import java.util.List;

public interface CacheSyncJobService {
    List<CacheSyncJobEntity> fetchAndMarkJobsAsProcessing();
}
