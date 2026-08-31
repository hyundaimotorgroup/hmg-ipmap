package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.projection.BatchStatusDetailProjection;
import com.hmg.ipmap.ingestion.file.projection.BatchStatusProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BatchRunRepository extends JpaRepository<BatchRunEntity, Long> {

    Optional<BatchRunEntity> findByJobId(String jobId);

    List<BatchRunEntity> findByStatus(BatchRunStatusEnum status);

    /**
     * Atomically updates the status of a batch run only if it is currently in the expected status.
     * Returns the number of rows updated (1 on success, 0 if the status has already changed). Used
     * to prevent duplicate job launches across multiple instances.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE BatchRunEntity b SET b.status = :newStatus "
                    + "WHERE b.jobId = :jobId AND b.status = :expectedStatus")
    int updateStatusConditionally(
            @Param("jobId") String jobId,
            @Param("expectedStatus") BatchRunStatusEnum expectedStatus,
            @Param("newStatus") BatchRunStatusEnum newStatus);

    /**
     * Atomically transitions a batch run to {@code IN_PROGRESS} and records its start time, but
     * only if the row is currently in {@code expectedStatus} (typically {@code READY}).
     *
     * <p>Returns 1 on success, 0 if another instance already made the transition first.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE BatchRunEntity b "
                    + "SET b.status = :newStatus, b.startedAt = :startedAt "
                    + "WHERE b.id = :id AND b.status = :expectedStatus")
    int startConditionally(
            @Param("id") Long id,
            @Param("expectedStatus") BatchRunStatusEnum expectedStatus,
            @Param("newStatus") BatchRunStatusEnum newStatus,
            @Param("startedAt") LocalDateTime startedAt);

    /**
     * Atomically transitions a batch run to a terminal status (COMPLETED, FAILED, CANCELED) and
     * records its timestamps, but only if the row is currently in {@code expectedStatus}.
     *
     * <p>Returns the number of rows updated: 1 on success, 0 if another instance already
     * transitioned out of {@code expectedStatus} first. This prevents the TOCTOU race that would
     * occur with a read-then-write pattern when N instances call {@code afterJob()} concurrently.
     *
     * <p>For FAILED: call twice if needed — first with {@code expectedStatus = IN_PROGRESS}, then
     * with {@code expectedStatus = COMPLETED} — so a late failure overrides an early COMPLETED.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE BatchRunEntity b "
                    + "SET b.status = :newStatus, b.startedAt = :startedAt, b.finishedAt = :finishedAt "
                    + "WHERE b.id = :id AND b.status = :expectedStatus")
    int finishConditionally(
            @Param("id") Long id,
            @Param("expectedStatus") BatchRunStatusEnum expectedStatus,
            @Param("newStatus") BatchRunStatusEnum newStatus,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("finishedAt") LocalDateTime finishedAt);

    @Query(
            value =
                    """
                SELECT
                    br.id AS batch_id,
                    br.job_id,
                    br.status AS batch_status,
                    br.started_at,
                    br.finished_at,
                    COUNT(DISTINCT bfz.id) AS total_file_zip,
                    ROUND(
                        CAST(COALESCE(SUM(processed.processed_count), 0) AS NUMERIC)
                        / NULLIF(SUM(bf.line_count) - SUM(bf.skip_count), 0) * 100
                    ) AS total_percentage
                FROM batch_run br
                INNER JOIN batch_file_zip bfz ON bfz.batch_id = br.id
                INNER JOIN batch_file bf ON bf.file_zip_id = bfz.id
                LEFT JOIN (
                    SELECT file_id, COUNT(*) AS processed_count
                    FROM batch_file_detail
                    WHERE status IN ('SUCCESS', 'ERROR')
                    GROUP BY file_id
                ) processed ON processed.file_id = bf.id
                WHERE br.job_id = ?
                GROUP BY br.id, br.job_id, br.status, br.started_at, br.finished_at;
                """,
            nativeQuery = true)
    Optional<BatchStatusProjection> getStatusByJobId(String jobId);

    @Query(
            value =
                    """
               SELECT
                   bfz.zip_type,
                   bfz.name AS file_name,
                   bfz.error_message,
                   CONCAT('step_', LOWER(bf.file_type)) AS step_name,
                   bf.status AS step_status,
                   bf.processed_at AS start_time,
                   CAST(bf.updated_at AS TIMESTAMP) AS end_time,
                   bf.error_message AS exit_message,
                   bf.line_count,
                   bf.skip_count,
                   COALESCE(processed.processed_count, 0) AS read_count,
                   COALESCE(processed.success_count, 0) AS write_count
               FROM batch_run br
               INNER JOIN batch_file_zip bfz ON bfz.batch_id = br.id
               INNER JOIN batch_file bf ON bf.file_zip_id = bfz.id
               LEFT JOIN (
                   SELECT
                       file_id,
                       COUNT(*) FILTER (WHERE status IN ('IN_PROGRESS', 'SUCCESS', 'ERROR')) AS processed_count,
                       COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_count
                   FROM batch_file_detail
                   GROUP BY file_id
               ) processed ON processed.file_id = bf.id
               WHERE br.job_id = :jobId
                 AND bfz.zip_type = :zipType;
               """,
            nativeQuery = true)
    List<BatchStatusDetailProjection> getStatusByZipType(String jobId, String zipType);
}
