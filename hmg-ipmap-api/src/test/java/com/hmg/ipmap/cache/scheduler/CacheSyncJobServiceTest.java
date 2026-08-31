package com.hmg.ipmap.cache.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.constants.CacheSyncJobStatus;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.cache.repository.CacheSyncJobRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CacheSyncJobServiceTest {

    @Mock private CacheSyncJobRepository cacheSyncJobRepository;

    @InjectMocks private CacheSyncJobServiceImpl cacheSyncJobService;

    private static final int DEFAULT_BATCH_SIZE = 200;

    @BeforeEach
    void setUp() {
        // Set default batch size
        ReflectionTestUtils.setField(cacheSyncJobService, "batchSize", DEFAULT_BATCH_SIZE);
    }

    @Test
    void fetchAndMarkJobsAsProcessing_WhenPendingJobsExist_ShouldFetchMarkAndReturn() {
        // Arrange
        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(5);
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(pendingJobs);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert
        assertNotNull(result);
        assertEquals(5, result.size());

        // Verify all jobs were marked as PROCESSING
        for (CacheSyncJobEntity job : result) {
            verify(job).setStatus(CacheSyncJobStatus.PROCESSING);
        }

        // Verify repository interactions
        verify(cacheSyncJobRepository)
                .findByStatusOrderByCreatedAtAsc(CacheSyncJobStatus.PENDING, expectedPageRequest);
        verify(cacheSyncJobRepository).saveAllAndFlush(pendingJobs);
    }

    @Test
    void fetchAndMarkJobsAsProcessing_WhenNoPendingJobs_ShouldReturnEmptyListWithoutSaving() {
        // Arrange
        List<CacheSyncJobEntity> emptyList = Collections.emptyList();
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(emptyList);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify fetch was called but save was NOT called
        verify(cacheSyncJobRepository)
                .findByStatusOrderByCreatedAtAsc(CacheSyncJobStatus.PENDING, expectedPageRequest);
        verify(cacheSyncJobRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void fetchAndMarkJobsAsProcessing_ShouldUseCorrectBatchSize() {
        // Arrange
        int customBatchSize = 50;
        ReflectionTestUtils.setField(cacheSyncJobService, "batchSize", customBatchSize);

        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(10);
        PageRequest expectedPageRequest = PageRequest.of(0, customBatchSize);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(pendingJobs);

        // Act
        cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Verify correct PageRequest was used
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(cacheSyncJobRepository)
                .findByStatusOrderByCreatedAtAsc(
                        eq(CacheSyncJobStatus.PENDING), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(customBatchSize, capturedPageable.getPageSize());
    }

    @Test
    void fetchAndMarkJobsAsProcessing_ShouldMarkAllJobsAsProcessingBeforeSaving() {
        // Arrange
        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(3);
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(pendingJobs);

        // Act
        cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Verify each job's status was set to PROCESSING
        for (CacheSyncJobEntity job : pendingJobs) {
            verify(job).setStatus(CacheSyncJobStatus.PROCESSING);
        }

        // Verify save was called with the same list
        verify(cacheSyncJobRepository).saveAllAndFlush(pendingJobs);
    }

    @Test
    void fetchAndMarkJobsAsProcessing_WhenSingleJob_ShouldProcessCorrectly() {
        // Arrange
        List<CacheSyncJobEntity> singleJob = createPendingJobs(1);
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(singleJob);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert
        assertEquals(1, result.size());
        verify(singleJob.getFirst()).setStatus(CacheSyncJobStatus.PROCESSING);
        verify(cacheSyncJobRepository).saveAllAndFlush(singleJob);
    }

    @Test
    void fetchAndMarkJobsAsProcessing_WhenMaxBatchSize_ShouldHandleCorrectly() {
        // Arrange
        int batchSize = 200;
        ReflectionTestUtils.setField(cacheSyncJobService, "batchSize", batchSize);

        List<CacheSyncJobEntity> fullBatch = createPendingJobs(batchSize);
        PageRequest expectedPageRequest = PageRequest.of(0, batchSize);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(fullBatch);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert
        assertEquals(batchSize, result.size());
        verify(cacheSyncJobRepository).saveAllAndFlush(fullBatch);

        // Verify all jobs marked as PROCESSING
        for (CacheSyncJobEntity job : fullBatch) {
            verify(job).setStatus(CacheSyncJobStatus.PROCESSING);
        }
    }

    @Test
    void fetchAndMarkJobsAsProcessing_ShouldQueryWithPendingStatusOnly() {
        // Arrange
        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(2);
        PageRequest pageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, pageRequest))
                .thenReturn(pendingJobs);

        // Act
        cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Verify only PENDING status is queried
        verify(cacheSyncJobRepository)
                .findByStatusOrderByCreatedAtAsc(
                        eq(CacheSyncJobStatus.PENDING), any(Pageable.class));
        verify(cacheSyncJobRepository, never())
                .findByStatusOrderByCreatedAtAsc(
                        eq(CacheSyncJobStatus.PROCESSING), any(Pageable.class));
        verify(cacheSyncJobRepository, never())
                .findByStatusOrderByCreatedAtAsc(
                        eq(CacheSyncJobStatus.COMPLETED), any(Pageable.class));
    }

    @Test
    void fetchAndMarkJobsAsProcessing_ShouldReturnSameListThatWasFetched() {
        // Arrange
        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(4);
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(pendingJobs);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Should return the exact same list instance
        assertSame(pendingJobs, result);
    }

    @Test
    void fetchAndMarkJobsAsProcessing_WhenEmptyList_ShouldReturnSameEmptyList() {
        // Arrange
        List<CacheSyncJobEntity> emptyList = Collections.emptyList();
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(emptyList);

        // Act
        List<CacheSyncJobEntity> result = cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Should return the same empty list
        assertSame(emptyList, result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchAndMarkJobsAsProcessing_ShouldUseSaveAllAndFlushNotSaveAll() {
        // Arrange
        List<CacheSyncJobEntity> pendingJobs = createPendingJobs(3);
        PageRequest expectedPageRequest = PageRequest.of(0, DEFAULT_BATCH_SIZE);

        when(cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, expectedPageRequest))
                .thenReturn(pendingJobs);

        // Act
        cacheSyncJobService.fetchAndMarkJobsAsProcessing();

        // Assert - Verify saveAllAndFlush is called (not just saveAll)
        verify(cacheSyncJobRepository).saveAllAndFlush(pendingJobs);
        verify(cacheSyncJobRepository, never()).saveAll(anyList());
    }

    private List<CacheSyncJobEntity> createPendingJobs(int count) {
        List<CacheSyncJobEntity> jobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CacheSyncJobEntity job = mock(CacheSyncJobEntity.class);
            jobs.add(job);
        }
        return jobs;
    }
}
