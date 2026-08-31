package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.ingestion.file.entity.BatchFileDetailEntity;
import com.hmg.ipmap.ingestion.file.job.error.FileDetailError;
import com.hmg.ipmap.ingestion.file.job.reader.RawLineData;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Custom JDBC operations for {@code batch_file_detail} that supplement the JPA repository. */
public interface BatchFileDetailRepositoryCustom {

    /** Bulk-inserts file detail records with status {@code INIT} using raw JDBC batch execution. */
    void saveAllInBatch(List<BatchFileDetailEntity> fileDetails);

    /** Batch-updates records to status {@code ERROR} with the given error messages. */
    void updateAllToErrorInBatch(List<FileDetailError> errors);

    /** Batch-updates records to status {@code SUCCESS}. */
    void updateAllToSuccessInBatch(List<Long> successIds);

    /**
     * Atomically claims up to {@code claimedSize} records for processing using {@code FOR UPDATE
     * SKIP LOCKED}, transitioning them from {@code INIT} to {@code IN_PROGRESS}.
     */
    List<RawLineData> claimForProcess(long batchId, String fileType, int claimedSize);

    /**
     * Resets {@code IN_PROGRESS} rows back to {@code INIT} when they have not been updated within
     * {@code olderThan}. Used to recover rows orphaned by a crashed instance so they can be
     * reclaimed by a healthy one.
     *
     * @return the number of rows reset
     */
    int resetOrphanedRows(long batchId, Duration olderThan);

    /**
     * Returns the subset of the given hashes that already exist in {@code batch_file_detail}. Used
     * to skip duplicate CSV lines across uploads.
     */
    Set<UUID> findExistingHashes(Set<UUID> hashes);

    /**
     * Bulk-updates all non-errored, non-promoted records whose {@code line_hash} is in {@code
     * hashes} to status {@code PROMOTE}. This signals that the line already has a corresponding
     * {@code ip_mapping} entry whose validity period should be extended.
     *
     * @param hashes hashes of lines that already exist in the DB
     */
    void updateAllToPromoteByHashes(Set<UUID> hashes);
}
