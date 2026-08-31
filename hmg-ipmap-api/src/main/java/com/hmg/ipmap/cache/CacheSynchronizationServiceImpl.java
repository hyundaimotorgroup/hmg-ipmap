package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.constants.CacheSyncJobStatus;
import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.CacheOperationError;
import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;
import com.hmg.ipmap.cache.entity.CacheSyncFailureEntity;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.cache.helper.CacheOperationMapper;
import com.hmg.ipmap.cache.repository.CacheSyncJobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class CacheSynchronizationServiceImpl implements CacheSynchronizationService {

    private final ObjectMapper objectMapper;
    private final CacheSyncFailureRepository cacheSyncFailureRepository;
    private final CacheSyncJobRepository cacheSyncJobRepository;
    private final CacheOperationService cacheOperationService;

    public CacheSynchronizationServiceImpl(
            ObjectMapper objectMapper,
            CacheSyncFailureRepository cacheSyncFailureRepository,
            CacheSyncJobRepository cacheSyncJobRepository,
            CacheOperationService cacheOperationService) {
        this.objectMapper = objectMapper;
        this.cacheSyncFailureRepository = cacheSyncFailureRepository;
        this.cacheSyncJobRepository = cacheSyncJobRepository;
        this.cacheOperationService = cacheOperationService;
    }

    @Transactional
    @Override
    public void syncCache(String action, String tableName, Object cacheDto) {
        log.info("syncCache start action={} tableName={} cacheDto={}", action, tableName, cacheDto);

        CacheOperationRequestDto request =
                buildCacheOperationRequestDto(action, tableName, cacheDto);

        if (request.getOperations().isEmpty()) {
            log.warn("Cache operation request is empty. action={} tableName={}", action, tableName);
            return;
        }
        log.info("Syncing {} operations", request.getOperations().size());
        CacheOperationResponseDto localResponse = cacheOperationService.updateCache(request);
        checkResponseAndUpdateFailureRecord(localResponse);
    }

    private CacheOperationRequestDto buildCacheOperationRequestDto(
            String action, String tableName, Object cacheDto) {
        CacheOperationRequestDto cacheOperationRequestDto = new CacheOperationRequestDto();
        List<CacheOperation> cacheOperations = new ArrayList<>();

        try {
            CacheOperation cacheOperation = new CacheOperation();
            cacheOperation.setAction(CacheOperationMapper.map(action, tableName));
            cacheOperation.setData(objectMapper.convertValue(cacheDto, new TypeReference<>() {}));
            cacheOperations.add(cacheOperation);
        } catch (Exception e) {
            log.error(
                    "Failed to build cache operation. action={} tableName={}",
                    action,
                    tableName,
                    e);
            updateFailureRecord(action, cacheDto, e.getMessage(), "UNKNOWN");
        }

        cacheOperationRequestDto.setOperations(cacheOperations);
        return cacheOperationRequestDto;
    }

    private void checkResponseAndUpdateFailureRecord(
            CacheOperationResponseDto cacheOperationResponseDto) {
        if (cacheOperationResponseDto != null && cacheOperationResponseDto.getErrorCount() > 0) {
            log.warn(
                    "Cache operation response contains error. Log the errors to database. errorCount={}",
                    cacheOperationResponseDto.getErrorCount());
            cacheOperationResponseDto
                    .getErrors()
                    .forEach(
                            error ->
                                    updateFailureRecord(
                                            error.getAction().name(),
                                            error.getData(),
                                            error.getErrorMessage(),
                                            "KR"));
        }
    }

    private void updateFailureRecord(
            String action, Object data, String errorMessage, String region) {
        try {
            CacheSyncFailureEntity cacheSyncFailureEntity = new CacheSyncFailureEntity();
            cacheSyncFailureEntity.setAction(action);
            cacheSyncFailureEntity.setData(objectMapper.writeValueAsString(data));
            cacheSyncFailureEntity.setRegion(region);
            cacheSyncFailureEntity.setError(errorMessage);
            cacheSyncFailureEntity.setStatus(CacheSyncJobStatus.FAILED);
            cacheSyncFailureEntity.setAttemptedAt(Instant.now());
            cacheSyncFailureRepository.save(cacheSyncFailureEntity);
        } catch (Exception e) {
            log.error(
                    "Failed to save cache sync failure record. action={} region={} errorMessage={} data={}",
                    action,
                    region,
                    errorMessage,
                    data,
                    e);
        }
    }

    @Transactional
    @Override
    public void syncCache(List<CacheSyncJobEntity> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            log.debug("No jobs to process in bulk");
            return;
        }

        log.info("Starting bulk cache sync for {} jobs", jobs.size());

        // Build bulk request with all operations
        CacheOperationRequestDto bulkRequest = buildBulkCacheOperationRequestDto(jobs);

        if (bulkRequest.getOperations().isEmpty()) {
            log.warn("Bulk cache operation request is empty after building");
            jobs.forEach(
                    job ->
                            updateJobStatus(
                                    job, CacheSyncJobStatus.FAILED, "Failed to build operation"));
            // Save failed jobs
            try {
                cacheSyncJobRepository.saveAll(jobs);
            } catch (Exception e) {
                log.error("Failed to save job status updates for empty operations", e);
            }
            return;
        }

        // Create a map to track jobs by their data for error mapping
        Map<String, CacheSyncJobEntity> jobMap = createJobMap(jobs);

        try {
            log.info("Bulk syncing {} operations", bulkRequest.getOperations().size());
            CacheOperationResponseDto localResponse =
                    cacheOperationService.updateCache(bulkRequest);
            processBulkResponse(localResponse, jobMap);
        } catch (Exception e) {
            log.error("Failed to sync bulk cache", e);
            jobs.forEach(job -> updateJobStatus(job, CacheSyncJobStatus.FAILED, e.getMessage()));
            // Save failed jobs
            try {
                cacheSyncJobRepository.saveAll(jobs);
            } catch (Exception saveException) {
                log.error(
                        "Failed to save job status updates after sync bulk failure", saveException);
            }
            return;
        }

        // Mark successfully processed jobs as COMPLETED
        jobs.forEach(
                job -> {
                    if (!CacheSyncJobStatus.FAILED.equals(job.getStatus())) {
                        updateJobStatus(job, CacheSyncJobStatus.COMPLETED, null);
                    }
                });

        // Batch save all job status updates in one database call
        try {
            cacheSyncJobRepository.saveAll(jobs);
            log.info("Successfully saved status for {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("Failed to save job status updates in batch", e);
        }

        log.info("Finished bulk cache sync for {} jobs", jobs.size());
    }

    private CacheOperationRequestDto buildBulkCacheOperationRequestDto(
            List<CacheSyncJobEntity> jobs) {
        CacheOperationRequestDto requestDto = new CacheOperationRequestDto();
        List<CacheOperation> operations = new ArrayList<>();

        for (CacheSyncJobEntity job : jobs) {
            try {
                CacheOperation operation = new CacheOperation();
                operation.setAction(CacheOperationMapper.map(job.getAction(), job.getTableName()));

                // Deserialize the data from JSON string
                Map<String, String> data =
                        objectMapper.readValue(job.getData(), new TypeReference<>() {});
                operation.setData(data);

                operations.add(operation);
            } catch (Exception e) {
                log.error(
                        "Failed to build cache operation for job. id={} action={} tableName={}",
                        job.getId(),
                        job.getAction(),
                        job.getTableName(),
                        e);
                updateJobStatus(
                        job,
                        CacheSyncJobStatus.FAILED,
                        "Failed to build operation: " + e.getMessage());
            }
        }

        requestDto.setOperations(operations);
        return requestDto;
    }

    private Map<String, CacheSyncJobEntity> createJobMap(List<CacheSyncJobEntity> jobs) {
        Map<String, CacheSyncJobEntity> jobMap = new HashMap<>();
        for (CacheSyncJobEntity job : jobs) {
            // Create a unique key based on action and data
            // Convert action to char : UPDATE -> u and DELETE -> d
            String key = job.getAction().toLowerCase().charAt(0) + ":" + job.getData();
            jobMap.put(key, job);
        }
        return jobMap;
    }

    private void processBulkResponse(
            CacheOperationResponseDto response, Map<String, CacheSyncJobEntity> jobMap) {
        if (response == null) {
            log.warn("Received null response");
            return;
        }

        log.info(
                "Bulk response from successCount={} errorCount={}",
                response.getSuccessCount(),
                response.getErrorCount());

        if (response.getErrorCount() > 0 && response.getErrors() != null) {
            for (CacheOperationError error : response.getErrors()) {
                try {
                    // Find the matching job by creating the same key
                    String dataJson = objectMapper.writeValueAsString(error.getData());
                    String key = error.getAction().operation + ":" + dataJson;

                    CacheSyncJobEntity job = jobMap.get(key);
                    if (job != null) {
                        log.warn(
                                "Marking job as FAILED. id={} region={} error={}",
                                job.getId(),
                                "KR",
                                error.getErrorMessage());
                        updateJobStatus(job, CacheSyncJobStatus.FAILED, error.getErrorMessage());

                        // Also log to failure table
                        updateFailureRecord(
                                error.getAction().name(),
                                error.getData(),
                                error.getErrorMessage(),
                                "KR");
                    }
                } catch (Exception e) {
                    log.error("Failed to process error response", e);
                }
            }
        }
    }

    private void updateJobStatus(CacheSyncJobEntity job, String status, String errorMessage) {
        job.setStatus(status);

        if (CacheSyncJobStatus.FAILED.equals(status) && errorMessage != null) {
            job.setErrorMessage(errorMessage);
            log.warn(
                    "Job marked as FAILED. id={} action={} tableName={} error={}",
                    job.getId(),
                    job.getAction(),
                    job.getTableName(),
                    errorMessage);
        }
    }

    @Transactional
    @Override
    public void saveCacheSyncJob(
            String action, String tableName, Object cacheDto, Instant sourceTimestamp) {
        try {
            CacheSyncJobEntity cacheSyncJobEntity = new CacheSyncJobEntity();
            cacheSyncJobEntity.setAction(action);
            cacheSyncJobEntity.setTableName(tableName);
            cacheSyncJobEntity.setData(objectMapper.writeValueAsString(cacheDto));
            cacheSyncJobEntity.setStatus(CacheSyncJobStatus.PENDING);
            cacheSyncJobEntity.setCreatedAt(sourceTimestamp);
            cacheSyncJobRepository.save(cacheSyncJobEntity);
            log.trace(
                    "Cache sync job saved successfully. action={} tableName={} cacheDto={}",
                    action,
                    tableName,
                    cacheDto);
        } catch (Exception e) {
            log.error(
                    "Failed to save cache sync job. action={} tableName={} cacheDto={}",
                    action,
                    tableName,
                    cacheDto,
                    e);
        }
    }
}
