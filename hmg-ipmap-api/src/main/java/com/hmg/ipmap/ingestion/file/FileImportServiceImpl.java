package com.hmg.ipmap.ingestion.file;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.exception.AlreadyExistException;
import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.common.exception.InternalServerErrorException;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.redis.DistributedLockService;
import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.dto.ImportTypeDetailDto;
import com.hmg.ipmap.ingestion.file.dto.JobControlRequestDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusImportTypeResponseDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusResponseDto;
import com.hmg.ipmap.ingestion.file.dto.StepDetailDto;
import com.hmg.ipmap.ingestion.file.dto.UploadResponseDto;
import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.enums.JobActionEnum;
import com.hmg.ipmap.ingestion.file.enums.ZipStatusEnum;
import com.hmg.ipmap.ingestion.file.event.UploadEvent;
import com.hmg.ipmap.ingestion.file.exception.JobControlException;
import com.hmg.ipmap.ingestion.file.job.event.BatchJobEventPublisher;
import com.hmg.ipmap.ingestion.file.projection.BatchStatusDetailProjection;
import com.hmg.ipmap.ingestion.file.projection.BatchStatusProjection;
import com.hmg.ipmap.ingestion.file.repository.BatchFileZipRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import com.hmg.ipmap.ingestion.provider.DataProvider;
import com.hmg.ipmap.ingestion.provider.ImportType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileImportServiceImpl implements FileImportService {

    private final BatchRunRepository batchRunRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BatchFileZipRepository batchFileZipRepository;
    private final DataProvider dataProvider;
    private static final String JOB_RUN_LOCK_PREFIX = "batch-job-run:";
    private static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(5);

    private final JobRepository jobRepository;

    private final JobOperator jobOperator;
    private final BatchJobEventPublisher batchJobEventPublisher;
    private final DistributedLockService distributedLockService;

    private final IngestionUploadProperties uploadProperties;

    /**
     * Validates and stores an uploaded ZIP file, then registers it against the given job run.
     *
     * <p>Processing steps:
     *
     * <ol>
     *   <li>Validates that the file has a {@code .zip} extension and a valid ZIP magic number.
     *   <li>Resolves {@code fileType} to a known {@link ImportType}; throws {@link
     *       com.hmg.ipmap.common.exception.BadRequestException} if unrecognized.
     *   <li>Finds or creates a {@link BatchRunEntity} for {@code jobId}.
     *   <li>Rejects the upload with {@link com.hmg.ipmap.common.exception.AlreadyExistException} if
     *       the job has already been executed, or if an extracted file of the same type exists.
     *   <li>Saves the file to the configured upload directory and persists a {@link
     *       BatchFileZipEntity} record.
     *   <li>Publishes an {@link UploadEvent} to trigger downstream processing.
     * </ol>
     *
     * @param file the multipart ZIP file to upload
     * @param jobId the job identifier in {@code yyyy-MM-dd} format (e.g. {@code 2025-11-05})
     * @param importTypeKey the file category string (e.g. {@code city-zip}, {@code country-zip})
     * @throws com.hmg.ipmap.common.exception.BadRequestException if the file is not a valid ZIP or
     *     the import type is unrecognized
     * @throws com.hmg.ipmap.common.exception.AlreadyExistException if the job has already been
     *     executed or an extracted file of the same type already exists for this job
     */
    @Transactional
    @Override
    public UploadResponseDto upload(MultipartFile file, String jobId, String importTypeKey) {

        ImportType importType = dataProvider.resolveImportType(importTypeKey);
        importType.validate(file);

        BatchRunEntity batchRun =
                batchRunRepository
                        .findByJobId(jobId)
                        .map(
                                existing -> {
                                    if (existing.getStatus().isExecuted()) {
                                        throw new AlreadyExistException("job already executed");
                                    }
                                    return existing;
                                })
                        .orElseGet(
                                () -> {
                                    BatchRunEntity entity = new BatchRunEntity();
                                    entity.setJobId(jobId);
                                    entity.setStatus(BatchRunStatusEnum.RECEIVED);
                                    entity.setJobName(FILE_IMPORT_JOB_NAME);

                                    return batchRunRepository.save(entity);
                                });

        if (batchFileZipRepository.existsByBatchRunIdAndZipTypeAndStatus(
                batchRun.getId(), importType.name(), ZipStatusEnum.EXTRACTED)) {
            throw new AlreadyExistException(String.format("%s already uploaded", importTypeKey));
        }

        File tempFile = saveUploadedFile(file, uploadProperties.getFolder());

        BatchFileZipEntity fileZip = new BatchFileZipEntity();
        fileZip.setBatchRun(batchRun);
        fileZip.setPath(tempFile.getAbsolutePath());
        fileZip.setName(file.getOriginalFilename());
        fileZip.setStatus(ZipStatusEnum.INIT);
        fileZip.setZipType(importType.name());
        BatchFileZipEntity saved = batchFileZipRepository.saveAndFlush(fileZip);

        eventPublisher.publishEvent(new UploadEvent(this, saved.getId()));

        return UploadResponseDto.builder()
                .jobId(jobId)
                .importType(importType)
                .fileName(saved.getName())
                .build();
    }

    @Override
    public JobStatusResponseDto getJobStatus(String jobId) {
        BatchStatusProjection jobStatus =
                batchRunRepository
                        .getStatusByJobId(jobId)
                        .orElseThrow(() -> new NotFoundException("Job Status Not Found"));

        return JobStatusResponseDto.builder()
                .jobId(jobId)
                .status(jobStatus.getBatchStatus())
                .importTypeCount(jobStatus.getTotalFileZip())
                .totalPercentage(jobStatus.getTotalPercentage())
                .startedAt(
                        jobStatus.getStatedAt() == null ? "" : jobStatus.getStatedAt().toString())
                .finishedAt(
                        jobStatus.getFinishedAt() == null
                                ? ""
                                : jobStatus.getFinishedAt().toString())
                .build();
    }

    @Override
    public JobStatusImportTypeResponseDto getJobStatusByImportType(
            String jobId, String importTypeKey) {
        ImportType importType = dataProvider.resolveImportType(importTypeKey);

        List<BatchStatusDetailProjection> statusByZipTypes =
                batchRunRepository.getStatusByZipType(jobId, importType.name());

        if (statusByZipTypes.isEmpty()) {
            throw new NotFoundException(
                    String.format(
                            "Status With Job Id %s and type %s Not Found", jobId, importTypeKey));
        }

        Map<String, ImportTypeDetailDto> mapFileType = new HashMap<>();

        Map<String, StepDetailDto> steps = new HashMap<>();
        String zipName = "";
        String fileName = "";
        String errorMessage = "";
        int totalLineCount = 0;
        int totalReadCount = 0;
        int totalSkipCount = 0;
        for (BatchStatusDetailProjection status : statusByZipTypes) {
            StepDetailDto step = new StepDetailDto();
            step.setStatus(status.getStepStatus());
            step.setExitMessage(status.getExitMessage());
            step.setStartedAt(
                    Optional.ofNullable(status.getStartTime()).map(Object::toString).orElse(null));
            step.setFinishedAt(
                    Optional.ofNullable(status.getEndTime()).map(Object::toString).orElse(null));

            zipName = status.getZipType();
            fileName = status.getFileName();
            errorMessage = status.getExitMessage();
            totalLineCount += status.getLineCount();
            totalReadCount += status.getReadCount();
            totalSkipCount += status.getSkipCount();

            steps.put(status.getStepName(), step);
        }
        int progressPercentage =
                calculatePercentage(Math.max(0, totalLineCount - totalSkipCount), totalReadCount);

        ImportTypeDetailDto fileDetailDto =
                ImportTypeDetailDto.builder()
                        .fileName(fileName)
                        .errorMessage(errorMessage)
                        .progressPercentage(progressPercentage)
                        .steps(steps)
                        .build();
        mapFileType.put(zipName.toLowerCase(), fileDetailDto);

        return JobStatusImportTypeResponseDto.builder().fileTypes(mapFileType).build();
    }

    @Override
    public JobStatusResponseDto control(String jobId, JobControlRequestDto dto) {
        Optional<JobActionEnum> jobActionEnum = JobActionEnum.fromValue(dto.getAction());
        if (jobActionEnum.isEmpty())
            throw new BadRequestException("Invalid action: " + dto.getAction());

        BatchRunEntity batchRun =
                batchRunRepository
                        .findByJobId(jobId)
                        .orElseThrow(() -> new NotFoundException("No job found for ID: " + jobId));

        switch (jobActionEnum.get()) {
            case JobActionEnum.RUN -> {
                return launchJob(jobId);
            }
            case JobActionEnum.CANCEL -> {
                if (!batchRun.getStatus().isExecuted() || cancelJob(batchRun)) {
                    batchRun.setStatus(BatchRunStatusEnum.CANCELED);
                    batchRunRepository.save(batchRun);

                    JobStatusResponseDto responseDto = new JobStatusResponseDto();
                    responseDto.setJobId(jobId);
                    responseDto.setStatus(BatchRunStatusEnum.CANCELED.toString());

                    return responseDto;
                } else {
                    throw new JobControlException(HttpStatus.CONFLICT, "No Batch Cancelled");
                }
            }
            default ->
                    throw new JobControlException(
                            HttpStatus.BAD_REQUEST, "Invalid action: " + dto.getAction());
        }
    }

    private JobStatusResponseDto launchJob(String jobId) {
        startJob(jobId, UserContextHolder.get().id());
        return getJobStatus(jobId);
    }

    @Override
    public void startJob(String jobId, Long userId) {
        if (!distributedLockService.tryLock(JOB_RUN_LOCK_PREFIX + jobId, LOCK_WAIT_TIME)) {
            throw new JobControlException(
                    HttpStatus.CONFLICT,
                    "Job is already being launched by another instance: " + jobId);
        }
        try {
            // Re-read inside the lock to get the authoritative current status.
            BatchRunEntity batchRun =
                    batchRunRepository
                            .findByJobId(jobId)
                            .orElseThrow(
                                    () -> new NotFoundException("No job found for ID: " + jobId));
            if (batchRun.getStatus().isExecuted()) {
                throw new JobControlException(
                        HttpStatus.CONFLICT, "Job already executed: " + batchRun.getStatus());
            }
            if (batchRun.getStatus().equals(BatchRunStatusEnum.UPLOADING)) {
                throw new JobControlException(
                        HttpStatus.CONFLICT, "Job not ready to execute: " + batchRun.getStatus());
            }

            if (batchRun.getStatus().equals(BatchRunStatusEnum.RECEIVED)) {
                throw new JobControlException(
                        HttpStatus.CONFLICT,
                        "Job cannot be executed: ZIP processing failed. Please re-upload the file.");
            }

            // Atomic conditional update: only succeeds if status is still READY.
            // Prevents a second instance that acquired the lock after us from re-launching.
            int updated =
                    batchRunRepository.updateStatusConditionally(
                            jobId, BatchRunStatusEnum.READY, BatchRunStatusEnum.IN_PROGRESS);
            if (updated == 0) {
                throw new JobControlException(
                        HttpStatus.CONFLICT,
                        "Job status changed concurrently, current status: " + batchRun.getStatus());
            }
        } finally {
            distributedLockService.unlock(JOB_RUN_LOCK_PREFIX + jobId);
        }

        batchJobEventPublisher.publish(jobId, userId);
    }

    private boolean cancelJob(BatchRunEntity batchRun) {
        Set<JobExecution> runningExecutions =
                jobRepository.findRunningJobExecutions(batchRun.getJobName());
        boolean result = true;
        for (JobExecution execution : runningExecutions) {
            try {
                result = result && jobOperator.stop(execution);
            } catch (JobExecutionNotRunningException e) {
                log.warn("Job is not running.", e);
            }
        }
        return result;
    }

    private int calculatePercentage(int processedCount, int readCount) {
        if (processedCount == 0) {
            return 0;
        }
        return (int) Math.round((readCount * 100.0) / processedCount);
    }

    private File saveUploadedFile(MultipartFile file, String targetDir) {
        File dir = new File(targetDir);
        if (!dir.exists()) {
            boolean status = dir.mkdirs();
            if (!status) throw new InternalServerErrorException("Unable to create directory");
        }

        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        String safeFilename = UUID.randomUUID() + "_" + Paths.get(originalFilename).getFileName();
        File tempFile = new File(dir, safeFilename);
        try {
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new InternalServerErrorException(
                    "Failed to save uploaded file: " + originalFilename, e);
        }
    }
}
