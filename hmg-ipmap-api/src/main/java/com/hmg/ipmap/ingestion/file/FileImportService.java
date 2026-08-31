package com.hmg.ipmap.ingestion.file;

import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.common.exception.ConflictException;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ingestion.file.dto.JobControlRequestDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusImportTypeResponseDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusResponseDto;
import com.hmg.ipmap.ingestion.file.dto.UploadResponseDto;
import com.hmg.ipmap.ingestion.file.exception.JobControlException;
import com.hmg.ipmap.ingestion.provider.ImportType;
import org.springframework.web.multipart.MultipartFile;

public interface FileImportService {

    String FILE_IMPORT_JOB_NAME = "fileImportJob";

    /**
     * Validates and stores an uploaded ZIP file, then registers it against the given job run.
     *
     * @param file the multipart ZIP file to upload
     * @param jobId the job identifier in {@code yyyy-MM-dd} format (e.g. {@code 2025-11-05})
     * @param importTypeKey the file category string (e.g. {@code location-zip}, {@code
     *     ip-block-zip})
     * @throws com.hmg.ipmap.common.exception.BadRequestException if the file is not a valid ZIP,
     *     the file type is unrecognized, the job ID format is invalid, or the job ID is a back-date
     * @throws com.hmg.ipmap.common.exception.AlreadyExistException if the job has already been
     *     executed or an extracted file of the same type already exists for this job
     */
    UploadResponseDto upload(MultipartFile file, String jobId, String importTypeKey);

    /**
     * Executes a control action ({@code RUN} or {@code CANCEL}) on the batch job identified by
     * {@code jobId}.
     *
     * @param jobId the job identifier in {@code yyyy-MM-dd} format
     * @param dto the control request containing the action to perform
     * @return a {@link JobStatusResponseDto} reflecting the updated job state
     * @throws BadRequestException if the action value is unrecognized
     * @throws NotFoundException if no job record exists for {@code jobId}
     * @throws ConflictException if the job is already running, has been executed, or the status
     *     changed concurrently during a {@code RUN} action
     * @throws JobControlException if the job could not be canceled or the job is not ready to run
     */
    JobStatusResponseDto control(String jobId, JobControlRequestDto dto);

    /**
     * Retrieves the current execution status of a batch job by its job ID.
     *
     * @param jobId the unique identifier of the batch job to query
     * @return a {@link JobStatusResponseDto} containing the job's status details
     * @throws com.hmg.ipmap.common.exception.NotFoundException if no batch job exists for the given
     *     {@code jobId}
     */
    JobStatusResponseDto getJobStatus(String jobId);

    /**
     * Retrieves the detailed execution status of a batch job filtered by file type.
     *
     * @param jobId the unique identifier of the batch job to query
     * @param importTypeKey the file type string to filter by (must match a valid {@link ImportType}
     *     value)
     * @return a {@link JobStatusImportTypeResponseDto} containing per-file-type step details and
     *     progress
     * @throws com.hmg.ipmap.common.exception.BadRequestException if {@code importTypeKey} does not
     *     correspond to a known {@link ImportType}
     * @throws com.hmg.ipmap.common.exception.NotFoundException if no status records exist for the
     *     given {@code jobId} and {@code importTypeKey}
     */
    JobStatusImportTypeResponseDto getJobStatusByImportType(String jobId, String importTypeKey);

    /**
     * Programmatically starts a batch job, transitioning the batch run from {@code READY} to {@code
     * IN_PROGRESS} and broadcasting the start event to all instances via Redis pub/sub.
     *
     * <p>This is the internal equivalent of calling {@link #control} with action {@code RUN}. It
     * accepts an explicit {@code userId} so it can be called from scheduled tasks that have no HTTP
     * user context.
     *
     * @param jobId the job identifier in {@code yyyy-MM-dd} format
     * @param userId the user (or system) ID to associate with the job execution
     * @throws NotFoundException if no batch run exists for {@code jobId}
     * @throws ConflictException if the job is already executing, already finished, or its status
     *     changed concurrently (another instance won the launch race)
     * @throws JobControlException if the batch run is still in {@code UPLOADING} state
     */
    void startJob(String jobId, Long userId);
}
