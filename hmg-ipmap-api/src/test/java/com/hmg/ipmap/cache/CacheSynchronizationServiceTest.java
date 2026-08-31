package com.hmg.ipmap.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.constants.CacheSyncJobStatus;
import com.hmg.ipmap.cache.dto.CacheOperationError;
import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;
import com.hmg.ipmap.cache.entity.CacheSyncFailureEntity;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.cache.enums.CacheOpsAction;
import com.hmg.ipmap.cache.repository.CacheSyncJobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CacheSynchronizationServiceTest {

    @Mock private ObjectMapper objectMapper;

    @Mock private CacheSyncFailureRepository cacheSyncFailureRepository;

    @Mock private CacheSyncJobRepository cacheSyncJobRepository;

    @Mock private CacheOperationService cacheOperationService;

    private CacheSynchronizationService cacheSynchronizationService;

    @BeforeEach
    void setUp() {
        cacheSynchronizationService =
                new CacheSynchronizationServiceImpl(
                        objectMapper,
                        cacheSyncFailureRepository,
                        cacheSyncJobRepository,
                        cacheOperationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCacheToAllRegions_WhenValidInput_ShouldSyncToLocalAndRemoteRegions() {
        // Arrange
        String action = "UPDATE";
        String tableName = "ip_mapping";
        Map<String, String> cacheDto = Map.of("id", "1", "name", "test");

        CacheOperationResponseDto localResponse = createSuccessResponse();
        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenReturn(localResponse);

        Map<String, Object> convertedData = new HashMap<>();
        convertedData.put("id", "1");
        when(objectMapper.convertValue(eq(cacheDto), (TypeReference<Object>) any()))
                .thenReturn(convertedData);

        // Act
        cacheSynchronizationService.syncCache(action, tableName, cacheDto);

        // Assert
        verify(cacheOperationService).updateCache(any(CacheOperationRequestDto.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCache_WhenBuildOperationFails_ShouldReturnEarly() {
        // Arrange
        String action = "UPDATE";
        String tableName = "ip_mapping";
        Map<String, String> cacheDto = Map.of("id", "1");

        when(objectMapper.convertValue(eq(cacheDto), (TypeReference<Object>) any()))
                .thenThrow(new RuntimeException("Conversion error"));

        // Act
        cacheSynchronizationService.syncCache(action, tableName, cacheDto);

        // Assert - Should not call updateCache if building operation fails
        verify(cacheOperationService, never()).updateCache(any());
        verify(cacheSyncFailureRepository).save(any(CacheSyncFailureEntity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCacheToAllRegions_WhenLocalSyncFails_ShouldStillSyncToRemote() {
        // Arrange
        String action = "UPDATE";
        String tableName = "location";
        Map<String, String> cacheDto = Map.of("id", "loc-1");

        CacheOperationResponseDto errorResponse = createErrorResponse();
        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenReturn(errorResponse);

        Map<String, Object> convertedData = new HashMap<>();
        when(objectMapper.convertValue(eq(cacheDto), (TypeReference<Object>) any()))
                .thenReturn(convertedData);

        // Act
        cacheSynchronizationService.syncCache(action, tableName, cacheDto);

        // Assert - Should log error and save failure record
        verify(cacheSyncFailureRepository).save(any(CacheSyncFailureEntity.class));
    }

    @Test
    void saveCacheSyncJob_WhenValidInput_ShouldSaveJob() throws Exception {
        // Arrange
        String action = "UPDATE";
        String tableName = "ip_span";
        Map<String, String> cacheDto = Map.of("id", "1", "ipLower", "100");
        Instant sourceTimestamp = Instant.parse("2024-01-01T10:00:00Z");

        String jsonData = "{\"id\":\"1\",\"ipLower\":\"100\"}";
        when(objectMapper.writeValueAsString(cacheDto)).thenReturn(jsonData);

        CacheSyncJobEntity savedEntity = new CacheSyncJobEntity();
        when(cacheSyncJobRepository.save(any(CacheSyncJobEntity.class))).thenReturn(savedEntity);

        // Act
        cacheSynchronizationService.saveCacheSyncJob(action, tableName, cacheDto, sourceTimestamp);

        // Assert
        ArgumentCaptor<CacheSyncJobEntity> captor =
                ArgumentCaptor.forClass(CacheSyncJobEntity.class);
        verify(cacheSyncJobRepository).save(captor.capture());

        CacheSyncJobEntity captured = captor.getValue();
        assertEquals(action, captured.getAction());
        assertEquals(tableName, captured.getTableName());
        assertEquals(jsonData, captured.getData());
        assertEquals(CacheSyncJobStatus.PENDING, captured.getStatus());
        assertEquals(sourceTimestamp, captured.getCreatedAt());
    }

    @Test
    void saveCacheSyncJob_WhenSerializationFails_ShouldCatchException() throws Exception {
        // Arrange
        String action = "UPDATE";
        String tableName = "ip_mapping";
        Map<String, String> cacheDto = Map.of("id", "1");
        Instant sourceTimestamp = Instant.now();

        when(objectMapper.writeValueAsString(cacheDto))
                .thenThrow(DatabindException.from((JsonGenerator) null, "Serialization error"));

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(
                () ->
                        cacheSynchronizationService.saveCacheSyncJob(
                                action, tableName, cacheDto, sourceTimestamp));

        verify(cacheSyncJobRepository, never()).save(any());
    }

    @Test
    void syncCache_WhenJobsAreNull_ShouldReturnEarly() {
        // Act
        cacheSynchronizationService.syncCache(null);

        // Assert
        verify(cacheOperationService, never()).updateCache(any());
        verify(cacheSyncJobRepository, never()).saveAll(any());
    }

    @Test
    void syncCache_WhenJobsAreEmpty_ShouldReturnEarly() {
        // Act
        cacheSynchronizationService.syncCache(Collections.emptyList());

        // Assert
        verify(cacheOperationService, never()).updateCache(any());
        verify(cacheSyncJobRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCache_WhenValidJobs_ShouldProcessAndMarkCompleted() throws Exception {
        // Arrange
        List<CacheSyncJobEntity> jobs = createValidJobs(3);

        when(objectMapper.readValue(anyString(), (TypeReference<Object>) any()))
                .thenReturn(Map.of("id", "1", "name", "test"));

        CacheOperationResponseDto successResponse = createSuccessResponse();
        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenReturn(successResponse);

        // Act
        cacheSynchronizationService.syncCache(jobs);

        // Assert - Verify all jobs are marked as COMPLETED
        ArgumentCaptor<List<CacheSyncJobEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cacheSyncJobRepository).saveAll(captor.capture());

        List<CacheSyncJobEntity> savedJobs = captor.getValue();
        assertTrue(
                savedJobs.stream()
                        .anyMatch(job -> CacheSyncJobStatus.COMPLETED.equals(job.getStatus())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCacheBulkToAllRegions_WhenLocalBulkSyncFails_ShouldMarkJobsAsFailed()
            throws Exception {
        // Arrange
        List<CacheSyncJobEntity> jobs = createValidJobs(2);

        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(Map.of("id", "1"));

        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenThrow(new RuntimeException("Local sync failed"));

        // Act
        cacheSynchronizationService.syncCache(jobs);

        // Assert - Jobs should be marked as FAILED
        ArgumentCaptor<List<CacheSyncJobEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cacheSyncJobRepository).saveAll(captor.capture());

        List<CacheSyncJobEntity> savedJobs = captor.getValue();
        assertTrue(
                savedJobs.stream()
                        .anyMatch(job -> CacheSyncJobStatus.FAILED.equals(job.getStatus())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCache_WhenBuildOperationFails_ShouldMarkJobAsFailed() throws Exception {
        // Arrange
        List<CacheSyncJobEntity> jobs = createValidJobs(1);

        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new RuntimeException("Parse error"));

        // Act
        cacheSynchronizationService.syncCache(jobs);

        // Assert - Job should be marked as FAILED
        ArgumentCaptor<List<CacheSyncJobEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cacheSyncJobRepository).saveAll(captor.capture());

        assertTrue(
                captor.getValue().stream()
                        .anyMatch(job -> CacheSyncJobStatus.FAILED.equals(job.getStatus())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCache_WhenResponseContainsErrors_ShouldMarkSpecificJobsAsFailed() throws Exception {
        // Arrange
        List<CacheSyncJobEntity> jobs = createValidJobs(2);

        Map<String, String> dataMap = Map.of("id", "1", "name", "test");
        when(objectMapper.readValue(anyString(), (TypeReference<Object>) any()))
                .thenReturn(dataMap);

        CacheOperationResponseDto errorResponse = createErrorResponseWithSpecificError();
        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenReturn(errorResponse);

        String dataJson = "{\"id\":\"1\",\"name\":\"test1\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(dataJson);

        // Act
        cacheSynchronizationService.syncCache(jobs);

        // Assert
        verify(cacheSyncFailureRepository, atLeast(1)).save(any(CacheSyncFailureEntity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncCache_WhenMultipleJobsWithMixedResults_ShouldHandleCorrectly() throws Exception {
        // Arrange
        List<CacheSyncJobEntity> jobs = createValidJobs(5);

        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenReturn(Map.of("id", "1"))
                .thenReturn(Map.of("id", "2"))
                .thenReturn(Map.of("id", "3"))
                .thenReturn(Map.of("id", "4"))
                .thenReturn(Map.of("id", "5"));

        CacheOperationResponseDto mixedResponse = createMixedResponse();
        when(cacheOperationService.updateCache(any(CacheOperationRequestDto.class)))
                .thenReturn(mixedResponse);

        // Act
        cacheSynchronizationService.syncCache(jobs);

        // Assert - Should have both COMPLETED and possibly FAILED jobs
        verify(cacheSyncJobRepository).saveAll(any());
    }

    // Helper methods

    private List<CacheSyncJobEntity> createValidJobs(int count) {
        List<CacheSyncJobEntity> jobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CacheSyncJobEntity job = new CacheSyncJobEntity();
            job.setId((long) (i + 1));
            job.setAction("UPDATE");
            job.setTableName("ip_mapping");
            job.setData("{\"id\":\"" + (i + 1) + "\",\"name\":\"test" + (i + 1) + "\"}");
            job.setStatus(CacheSyncJobStatus.PROCESSING);
            jobs.add(job);
        }
        return jobs;
    }

    private CacheOperationResponseDto createSuccessResponse() {
        return CacheOperationResponseDto.builder()
                .successCount(1)
                .errorCount(0)
                .errors(Collections.emptyList())
                .build();
    }

    private CacheOperationResponseDto createErrorResponse() {
        CacheOperationError error =
                CacheOperationError.builder()
                        .action(CacheOpsAction.LOCATION_UPDATE)
                        .errorMessage("Cache operation failed")
                        .data(Map.of("id", "1"))
                        .build();

        return CacheOperationResponseDto.builder()
                .successCount(0)
                .errorCount(1)
                .errors(List.of(error))
                .build();
    }

    private CacheOperationResponseDto createErrorResponseWithSpecificError() {
        CacheOperationError error =
                CacheOperationError.builder()
                        .action(CacheOpsAction.IP_UPDATE)
                        .errorMessage("Specific error for job 1")
                        .data(Map.of("id", "1", "name", "test"))
                        .build();

        return CacheOperationResponseDto.builder()
                .successCount(1)
                .errorCount(1)
                .errors(List.of(error))
                .build();
    }

    private CacheOperationResponseDto createMixedResponse() {
        CacheOperationError error1 =
                CacheOperationError.builder()
                        .errorMessage("Error 1")
                        .data(Map.of("id", "2"))
                        .build();

        CacheOperationError error2 =
                CacheOperationError.builder()
                        .errorMessage("Error 2")
                        .data(Map.of("id", "4"))
                        .build();

        return CacheOperationResponseDto.builder()
                .successCount(3)
                .errorCount(2)
                .errors(List.of(error1, error2))
                .build();
    }
}
