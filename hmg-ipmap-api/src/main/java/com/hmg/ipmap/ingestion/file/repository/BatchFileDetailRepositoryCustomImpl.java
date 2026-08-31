package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.exception.InternalServerErrorException;
import com.hmg.ipmap.ingestion.file.entity.BatchFileDetailEntity;
import com.hmg.ipmap.ingestion.file.job.error.FileDetailError;
import com.hmg.ipmap.ingestion.file.job.reader.RawLineData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BatchFileDetailRepositoryCustomImpl implements BatchFileDetailRepositoryCustom {

    /**
     * Maximum number of UUIDs per {@code IN} clause in {@link
     * BatchFileDetailRepositoryCustom#findExistingHashes}.
     */
    private static final int HASH_IN_CLAUSE_MAX_SIZE = 1000;

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Bulk-inserts file detail records with status {@code INIT} using raw JDBC batch execution.
     * Manages its own transaction via {@code setAutoCommit(false)} / {@code commit()}.
     *
     * @param fileDetails records to insert
     * @throws InternalServerErrorException if the batch insert fails
     */
    @Override
    public void saveAllInBatch(List<BatchFileDetailEntity> fileDetails) {
        String sql =
                """
            insert into batch_file_detail (id, file_id, line_data, line_no, line_hash, status, created_at, created_by)
            values (DEFAULT, ?, ?, ?, ?, 'INIT', ?, ?)
            """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (BatchFileDetailEntity fileDetail : fileDetails) {
                ps.setLong(1, fileDetail.getBatchFile().getId());
                ps.setString(2, fileDetail.getLineData());
                ps.setLong(3, fileDetail.getLineNo());
                ps.setObject(4, fileDetail.getLineHash());
                ps.setTimestamp(5, Timestamp.from(fileDetail.getCreatedAt()));
                ps.setLong(6, fileDetail.getCreatedBy());

                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            log.error("Error when batch insert batch file detail", e);
            throw new InternalServerErrorException("Failed to save batch file details", e);
        }
    }

    /**
     * Batch-updates file detail records to status {@code ERROR} with the given error messages.
     *
     * @param errors list of errors containing the file detail ID and message; no-op if null or
     *     empty
     */
    @Override
    @Transactional
    public void updateAllToErrorInBatch(List<FileDetailError> errors) {
        if (CollectionUtils.isEmpty(errors)) {
            return;
        }

        String sql =
                """
                UPDATE batch_file_detail
                SET status = 'ERROR', error_message = :message,
                    updated_at = NOW(), updated_by = :updatedBy
                WHERE id = :id
                """;

        SqlParameterSource[] batch =
                errors.stream()
                        .map(
                                e ->
                                        new MapSqlParameterSource()
                                                .addValue("id", e.getFileDetailId())
                                                .addValue("message", e.getMessage())
                                                .addValue(
                                                        "updatedBy", UserContextHolder.get().id()))
                        .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
        log.warn("Bulk updated {} records to status : ERROR", errors.size());
    }

    /**
     * Batch-updates file detail records to status {@code SUCCESS}.
     *
     * @param successIds IDs of the records to mark as successful; no-op if null or empty
     */
    @Override
    @Transactional
    public void updateAllToSuccessInBatch(List<Long> successIds) {
        if (CollectionUtils.isEmpty(successIds)) {
            return;
        }

        String sql =
                """
                UPDATE batch_file_detail
                SET status = 'SUCCESS', updated_at = NOW(), updated_by = :updatedBy
                WHERE id = :id
                """;

        long updatedBy = UserContextHolder.get().id();

        SqlParameterSource[] batch =
                successIds.stream()
                        .map(
                                id ->
                                        new MapSqlParameterSource()
                                                .addValue("id", id)
                                                .addValue("updatedBy", updatedBy))
                        .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
        log.trace("{} Records updated to success", successIds.size());
    }

    /**
     * Atomically claims up to {@code claimedSize} file detail records for processing.
     *
     * <p>Uses a CTE with {@code FOR UPDATE SKIP LOCKED} to select and immediately transition rows
     * from {@code INIT} to {@code IN_PROGRESS} in a single statement, preventing concurrent workers
     * from claiming the same rows without blocking.
     *
     * @param batchId the batch to claim records from
     * @param fileType the file type to filter by
     * @param claimedSize maximum number of records to claim
     * @return the claimed records ordered by ID
     */
    @Override
    public List<RawLineData> claimForProcess(long batchId, String fileType, int claimedSize) {
        String sql =
                """
                WITH picked_rows AS (
                    SELECT bfd.id
                    FROM batch_file_detail bfd
                    JOIN batch_file bf
                      ON bfd.file_id = bf.id
                    WHERE bfd.status = 'INIT'
                      AND bf.batch_id = :batchId
                      AND bf.status = 'IN_PROGRESS'
                      AND bf.file_type = :fileType
                    LIMIT :claimedSize
                    FOR UPDATE OF bfd SKIP LOCKED
                ),
                updated_rows AS (
                    UPDATE batch_file_detail bfd
                    SET status = 'IN_PROGRESS', updated_at = NOW()
                    FROM picked_rows pr
                    WHERE bfd.id = pr.id
                    RETURNING bfd.id, bfd.line_data
                )
                SELECT id, line_data
                FROM updated_rows
                ORDER BY id
                """;

        SqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("batchId", batchId)
                        .addValue("fileType", fileType)
                        .addValue("claimedSize", claimedSize);

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new RawLineData(rs.getLong("id"), rs.getString("line_data")));
    }

    /**
     * Resets rows that have been stuck in {@code IN_PROGRESS} longer than {@code olderThan} back to
     * {@code INIT}. This recovers rows orphaned when an instance crashed mid-processing so a
     * healthy instance can reclaim them on the next read cycle.
     */
    @Override
    @Transactional
    public int resetOrphanedRows(long batchId, Duration olderThan) {
        String sql =
                """
                UPDATE batch_file_detail
                SET status = 'INIT', updated_at = NOW()
                WHERE status = 'IN_PROGRESS'
                  AND updated_at < NOW() - :seconds * INTERVAL '1 second'
                  AND file_id IN (
                      SELECT bf.id FROM batch_file bf
                      WHERE bf.batch_id = :batchId
                  )
                """;

        SqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("batchId", batchId)
                        .addValue("seconds", olderThan.getSeconds());

        return namedParameterJdbcTemplate.update(sql, params);
    }

    @Override
    @Transactional
    public void updateAllToPromoteByHashes(Set<UUID> hashes) {
        String sql =
                """
                UPDATE batch_file_detail
                SET status = 'PROMOTE', updated_at = NOW()
                WHERE line_hash IN (:hashes)
                  AND status NOT IN ('ERROR', 'PROMOTE')
                """;

        for (List<UUID> partition :
                ListUtils.partition(new ArrayList<>(hashes), HASH_IN_CLAUSE_MAX_SIZE)) {
            SqlParameterSource params = new MapSqlParameterSource("hashes", partition);
            namedParameterJdbcTemplate.update(sql, params);
        }
    }

    /**
     * Returns the subset of the given hashes that already exist in {@code batch_file_detail}.
     * Executes a single {@code IN} query; returns an empty set if {@code hashes} is empty.
     */
    @Override
    public Set<UUID> findExistingHashes(Set<UUID> hashes) {
        if (hashes.isEmpty()) {
            return Set.of();
        }

        String sql =
                """
                SELECT DISTINCT line_hash
                FROM batch_file_detail
                WHERE line_hash IN (:hashes)
                  AND status != 'ERROR'
                """;

        Set<UUID> result = new HashSet<>();
        for (List<UUID> partition :
                ListUtils.partition(new ArrayList<>(hashes), HASH_IN_CLAUSE_MAX_SIZE)) {
            SqlParameterSource params = new MapSqlParameterSource("hashes", partition);
            result.addAll(
                    namedParameterJdbcTemplate.query(
                            sql, params, (rs, rowNum) -> rs.getObject("line_hash", UUID.class)));
        }
        return result;
    }
}
