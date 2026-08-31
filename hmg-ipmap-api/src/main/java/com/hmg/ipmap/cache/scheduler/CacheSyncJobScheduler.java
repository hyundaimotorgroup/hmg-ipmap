package com.hmg.ipmap.cache.scheduler;

import com.hmg.ipmap.cache.CacheSynchronizationService;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.common.redis.DistributedLockService;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "cache-sync.job.enabled", havingValue = "true", matchIfMissing = true)
public class CacheSyncJobScheduler {

    private static final String LOCK_KEY = "cache:sync:job:lock";

    private final CacheSyncJobService cacheSyncJobService;
    private final CacheSynchronizationService cacheSynchronizationService;
    private final DistributedLockService distributedLockService;

    @Value("${cache-sync.job.lock.wait-time:1s}")
    private Duration lockWaitTime;

    public CacheSyncJobScheduler(
            CacheSyncJobService cacheSyncJobService,
            CacheSynchronizationService cacheSynchronizationService,
            DistributedLockService distributedLockService) {
        this.cacheSyncJobService = cacheSyncJobService;
        this.cacheSynchronizationService = cacheSynchronizationService;
        this.distributedLockService = distributedLockService;
    }

    @Scheduled(fixedDelayString = "${cache-sync.job.fixed-delay:5000}")
    public void processPendingCacheSyncJobs() {
        try {
            log.trace("Starting to process pending cache sync jobs");
            int batchCount = 0;
            while (true) {
                List<CacheSyncJobEntity> jobsToProcess;
                boolean lockAcquired = false;

                try {
                    // Acquire lock before fetching each batch
                    lockAcquired = distributedLockService.tryLock(LOCK_KEY, lockWaitTime);

                    if (!lockAcquired) {
                        log.trace(
                                "Could not acquire lock for batch #{}. Another server might be processing.",
                                batchCount + 1);
                        return;
                    }

                    log.trace("Lock acquired for batch #{}", batchCount + 1);

                    jobsToProcess = cacheSyncJobService.fetchAndMarkJobsAsProcessing();
                } finally {
                    // Release lock immediately after fetching and marking
                    if (lockAcquired) {
                        distributedLockService.unlock(LOCK_KEY);
                        log.trace("Lock released after fetching batch #{}", batchCount + 1);
                    }
                }

                // Check if there is no pending job
                if (jobsToProcess == null || jobsToProcess.isEmpty()) {
                    log.trace(
                            "No more pending cache sync jobs found. Processed {} batches",
                            batchCount);
                    return;
                }

                batchCount++;

                log.info("Processing batch #{} with {} jobs", batchCount, jobsToProcess.size());

                // Process jobs outside the lock (jobs are already marked as PROCESSING)
                cacheSynchronizationService.syncCache(jobsToProcess);

                log.info("Finished processing batch #{} ", batchCount);
            }
        } catch (Exception e) {
            log.error("Error processing cache sync jobs", e);
        }
    }
}
