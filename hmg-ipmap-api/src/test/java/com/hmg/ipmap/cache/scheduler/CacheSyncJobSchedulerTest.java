package com.hmg.ipmap.cache.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.CacheSynchronizationService;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.common.redis.DistributedLockService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CacheSyncJobSchedulerTest {

    private static final String LOCK_KEY = "cache:sync:job:lock";

    @Mock private CacheSyncJobService cacheSyncJobService;

    @Mock private CacheSynchronizationService cacheSynchronizationService;

    @Mock private DistributedLockService distributedLockService;

    @InjectMocks private CacheSyncJobScheduler cacheSyncJobScheduler;

    @BeforeEach
    void setUp() {
        // Set default properties using reflection
        ReflectionTestUtils.setField(cacheSyncJobScheduler, "lockWaitTime", Duration.ofSeconds(1));
    }

    @Test
    void processPendingCacheSyncJobs_WhenLockCannotBeAcquired_ShouldReturnEarly() {
        // Arrange
        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(false);

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert
        verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(distributedLockService, never()).unlock(LOCK_KEY);
        verify(cacheSyncJobService, never()).fetchAndMarkJobsAsProcessing();
        verify(cacheSynchronizationService, never()).syncCache(anyList());
    }

    @Test
    void processPendingCacheSyncJobs_WhenNoJobsFound_ShouldReleaseLockAndReturn() {
        // Arrange
        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenReturn(Collections.emptyList());

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert - Verify lock is acquired BEFORE fetching jobs and released AFTER
        InOrder inOrder =
                inOrder(distributedLockService, cacheSyncJobService, cacheSynchronizationService);

        inOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        inOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        inOrder.verify(distributedLockService).unlock(LOCK_KEY);

        verify(cacheSynchronizationService, never()).syncCache(anyList());
    }

    @Test
    void processPendingCacheSyncJobs_WhenJobsFound_ShouldProcessAndReleaseLock() {
        // Arrange
        List<CacheSyncJobEntity> jobs = createMockJobs(5);

        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenReturn(jobs)
                .thenReturn(Collections.emptyList()); // Second call returns empty

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert - Verify the order: lock -> fetch -> unlock -> process
        InOrder inOrder =
                inOrder(distributedLockService, cacheSyncJobService, cacheSynchronizationService);

        // First batch
        inOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        inOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        inOrder.verify(distributedLockService).unlock(LOCK_KEY);
        inOrder.verify(cacheSynchronizationService).syncCache(jobs);

        // Second batch (empty)
        inOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        inOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        inOrder.verify(distributedLockService).unlock(LOCK_KEY);

        // Verify processing happened
        verify(cacheSynchronizationService, times(1)).syncCache(jobs);
    }

    @Test
    void processPendingCacheSyncJobs_WhenMultipleBatches_ShouldProcessAllBatches() {
        // Arrange
        List<CacheSyncJobEntity> batch1 = createMockJobs(3);
        List<CacheSyncJobEntity> batch2 = createMockJobs(2);

        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenReturn(batch1)
                .thenReturn(batch2)
                .thenReturn(Collections.emptyList());

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert
        verify(cacheSyncJobService, times(3)).fetchAndMarkJobsAsProcessing();
        verify(cacheSynchronizationService).syncCache(batch1);
        verify(cacheSynchronizationService).syncCache(batch2);
        verify(distributedLockService, times(3)).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(distributedLockService, times(3)).unlock(LOCK_KEY);
    }

    @Test
    void processPendingCacheSyncJobs_WhenExceptionDuringProcessing_ShouldCatchAndLog() {
        // Arrange
        List<CacheSyncJobEntity> jobs = createMockJobs(3);

        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing()).thenReturn(jobs);
        doThrow(new RuntimeException("Processing error"))
                .when(cacheSynchronizationService)
                .syncCache(jobs);

        // Act - Should not throw exception
        assertDoesNotThrow(() -> cacheSyncJobScheduler.processPendingCacheSyncJobs());

        // Assert - Lock should still be released
        verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(distributedLockService).unlock(LOCK_KEY);
    }

    @Test
    void processPendingCacheSyncJobs_WhenExceptionDuringFetch_ShouldReleaseLock() {
        // Arrange
        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenThrow(new RuntimeException("Fetch error"));

        // Act - Should not throw exception
        assertDoesNotThrow(() -> cacheSyncJobScheduler.processPendingCacheSyncJobs());

        // Assert - Lock should be released in finally block
        verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(distributedLockService).unlock(LOCK_KEY);
        verify(cacheSynchronizationService, never()).syncCache(anyList());
    }

    @Test
    void processPendingCacheSyncJobs_VerifyLockAcquiredBeforeFetchAndReleasedAfter() {
        // Arrange
        List<CacheSyncJobEntity> jobs = createMockJobs(2);

        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenReturn(jobs)
                .thenReturn(Collections.emptyList());

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert - Critical test: Verify exact order of lock -> fetch -> unlock
        InOrder strictOrder = inOrder(distributedLockService, cacheSyncJobService);

        // First batch
        strictOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        strictOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        strictOrder.verify(distributedLockService).unlock(LOCK_KEY);

        // Second batch
        strictOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        strictOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        strictOrder.verify(distributedLockService).unlock(LOCK_KEY);
    }

    @Test
    void processPendingCacheSyncJobs_WhenNullJobsReturned_ShouldReleaseLockAndReturn() {
        // Arrange
        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing()).thenReturn(null);

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert
        InOrder inOrder = inOrder(distributedLockService, cacheSyncJobService);
        inOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        inOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        inOrder.verify(distributedLockService).unlock(LOCK_KEY);

        verify(cacheSynchronizationService, never()).syncCache(anyList());
    }

    @Test
    void processPendingCacheSyncJobs_ProcessingHappensOutsideLock() {
        // Arrange
        List<CacheSyncJobEntity> jobs = createMockJobs(3);

        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(true);
        when(cacheSyncJobService.fetchAndMarkJobsAsProcessing())
                .thenReturn(jobs)
                .thenReturn(Collections.emptyList());

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert - Processing should happen AFTER lock is released
        InOrder inOrder =
                inOrder(distributedLockService, cacheSyncJobService, cacheSynchronizationService);

        inOrder.verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        inOrder.verify(cacheSyncJobService).fetchAndMarkJobsAsProcessing();
        inOrder.verify(distributedLockService).unlock(LOCK_KEY);
        inOrder.verify(cacheSynchronizationService).syncCache(jobs); // Should happen AFTER unlock
    }

    @Test
    void processPendingCacheSyncJobs_WhenLockAcquisitionFails_ShouldNotCallUnlock() {
        // Arrange
        when(distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1))).thenReturn(false);

        // Act
        cacheSyncJobScheduler.processPendingCacheSyncJobs();

        // Assert - unlock should never be called if lock acquisition failed
        verify(distributedLockService).tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(distributedLockService, never()).unlock(LOCK_KEY);
        verify(cacheSyncJobService, never()).fetchAndMarkJobsAsProcessing();
    }

    private List<CacheSyncJobEntity> createMockJobs(int count) {
        List<CacheSyncJobEntity> jobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CacheSyncJobEntity job = mock(CacheSyncJobEntity.class);
            jobs.add(job);
        }
        return jobs;
    }
}
