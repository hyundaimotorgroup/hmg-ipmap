package com.hmg.ipmap.cache.event;

import com.hmg.ipmap.cache.CacheSynchronizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CacheUpdateEventListener {

    private final CacheSynchronizationService cacheSynchronizationService;

    public CacheUpdateEventListener(CacheSynchronizationService cacheSynchronizationService) {
        this.cacheSynchronizationService = cacheSynchronizationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("cacheSyncTaskExecutor")
    public void handleCacheUpdateEvent(CacheUpdateEvent event) {
        try {
            log.trace(
                    "Saving job to queue. action={} tableName={}",
                    event.action(),
                    event.tableName());
            cacheSynchronizationService.saveCacheSyncJob(
                    event.action(), event.tableName(), event.cacheDto(), event.sourceTimestamp());
        } catch (Exception e) {
            log.error(
                    "Failed to handle cache update event. action={} tableName={}",
                    event.action(),
                    event.tableName(),
                    e);
        }
    }
}
