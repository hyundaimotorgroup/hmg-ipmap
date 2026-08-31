package com.hmg.ipmap.ingestion.file.scheduler;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.ingestion.file.AsyncJobRunner;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepositoryCustom;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic scheduler that runs on every instance and provides two recovery mechanisms for
 * multi-instance batch processing:
 *
 * <ol>
 *   <li><b>Join recovery</b>: If this instance missed the Redis pub/sub job-start event (e.g. it
 *       was restarting), it detects {@code IN_PROGRESS} batch runs and joins by starting its own
 *       local Spring Batch execution. Work is distributed via the {@code SKIP LOCKED} claim
 *       mechanism in {@link BatchFileDetailRepositoryCustom#claimForProcess}.
 *   <li><b>Orphan recovery</b>: If a peer instance crashed mid-job, the rows it claimed remain
 *       stuck in {@code IN_PROGRESS} in {@code batch_file_detail}. After {@code
 *       app.ingestion.polling.orphan-timeout} the scheduler resets them to {@code INIT} so they can
 *       be reclaimed by any healthy instance on the next read cycle.
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchJobPollingScheduler {

    private final BatchRunRepository batchRunRepository;
    private final BatchFileDetailRepositoryCustom batchFileDetailRepository;
    private final AsyncJobRunner asyncJobRunner;

    @Value("${app.ingestion.polling.orphan-timeout:600s}")
    private Duration orphanTimeout;

    @Scheduled(fixedDelayString = "${app.ingestion.polling.fixed-delay:30000}")
    public void pollAndRecover() {
        List<BatchRunEntity> activeJobs =
                batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS);

        if (activeJobs.isEmpty()) {
            return;
        }

        for (BatchRunEntity batchRun : activeJobs) {
            recoverOrphanedRows(batchRun, orphanTimeout);
            joinIfNotRunningLocally(batchRun);
        }
    }

    private void recoverOrphanedRows(BatchRunEntity batchRun, Duration orphanTimeout) {
        int reset = batchFileDetailRepository.resetOrphanedRows(batchRun.getId(), orphanTimeout);
        if (reset > 0) {
            log.warn(
                    "Reset {} orphaned IN_PROGRESS row(s) to INIT for batchId={} (timeout={}s)"
                            + " — likely caused by a crashed instance",
                    reset,
                    batchRun.getId(),
                    orphanTimeout.getSeconds());
        }
    }

    private void joinIfNotRunningLocally(BatchRunEntity batchRun) {
        if (asyncJobRunner.isRunningLocally(batchRun.getJobId())) {
            return;
        }
        log.info(
                "This instance is not running jobId={} (batchId={}) — joining now",
                batchRun.getJobId(),
                batchRun.getId());

        Long userId = batchRun.getCreatedBy();
        if (userId == null) {
            log.warn("skip join running job due to userId is null");
            return;
        }

        // Provide a minimal UserContext so batch writers can resolve userId.
        // UserContextTaskDecorator propagates it from this thread to the async executor thread.
        UserContextHolder.set(new UserContext(userId, null, null, null, null, null, null));
        try {
            asyncJobRunner.run(batchRun.getJobId(), userId);
        } finally {
            UserContextHolder.clear();
        }
    }
}
