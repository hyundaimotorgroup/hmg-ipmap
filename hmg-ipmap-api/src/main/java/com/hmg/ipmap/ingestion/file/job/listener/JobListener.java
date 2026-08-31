package com.hmg.ipmap.ingestion.file.job.listener;

import com.hmg.ipmap.common.redis.DistributedLockService;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.job.JobParameter;
import com.hmg.ipmap.ingestion.file.job.tasklet.LocationCoordinationTasklet;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class JobListener implements JobExecutionListener {

    private BatchRunRepository batchRunRepository;
    private DistributedLockService distributedLockService;
    private JobParameter jobParameter;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        this.printSeparator();
        log.info("JOB STARTED: {}", jobExecution.getJobInstance().getJobName());
        log.info("Start Time: {}", jobExecution.getStartTime());

        // Only the first instance transitions READY → IN_PROGRESS; the others see 0 rows updated
        // and skip — startedAt is recorded once from the winning instance's execution time.
        Long batchId = jobParameter.getBatchId();
        int started =
                batchRunRepository.startConditionally(
                        batchId,
                        BatchRunStatusEnum.READY,
                        BatchRunStatusEnum.IN_PROGRESS,
                        jobExecution.getStartTime());
        if (started == 0) {
            log.debug(
                    "beforeJob: status already past READY for batchId={} — skipping update",
                    batchId);
        }

        this.printSeparator();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        this.printSeparator();
        log.info("JOB FINISHED: {}", jobExecution.getJobInstance().getJobName());
        log.info("Status: {}", jobExecution.getStatus());
        log.info("End Time: {}", jobExecution.getEndTime());
        BatchStatus status = jobExecution.getStatus();

        if (status == BatchStatus.COMPLETED) {
            // Only the first instance to complete transitions IN_PROGRESS → COMPLETED.
            // If another instance already wrote FAILED, the conditional update returns 0 and we
            // skip — FAILED must not be overwritten by a later COMPLETED.
            finishConditionally(
                    jobExecution, BatchRunStatusEnum.IN_PROGRESS, BatchRunStatusEnum.COMPLETED);
        } else if (status == BatchStatus.STOPPED) {
            finishConditionally(
                    jobExecution, BatchRunStatusEnum.IN_PROGRESS, BatchRunStatusEnum.CANCELED);
            releaseLockIfHeld();
        } else if (status == BatchStatus.FAILED) {
            // FAILED must override both IN_PROGRESS and COMPLETED: one instance failing makes the
            // whole batch failed even if others already reported COMPLETED.
            int updated =
                    finishConditionally(
                            jobExecution,
                            BatchRunStatusEnum.IN_PROGRESS,
                            BatchRunStatusEnum.FAILED);
            if (updated == 0) {
                // Status was no longer IN_PROGRESS — try overriding a racing COMPLETED.
                finishConditionally(
                        jobExecution, BatchRunStatusEnum.COMPLETED, BatchRunStatusEnum.FAILED);
            }
            releaseLockIfHeld();
        }

        this.printSeparator();
    }

    /**
     * Atomically transitions the batch run to {@code newStatus} only if it is currently in {@code
     * expectedStatus}. Logs a warning if the transition was skipped (another instance won the
     * race).
     *
     * @return the number of rows updated (1 = success, 0 = skipped)
     */
    private int finishConditionally(
            JobExecution jobExecution,
            BatchRunStatusEnum expectedStatus,
            BatchRunStatusEnum newStatus) {
        Long batchId = jobParameter.getBatchId();
        int updated =
                batchRunRepository.finishConditionally(
                        batchId,
                        expectedStatus,
                        newStatus,
                        jobExecution.getStartTime(),
                        jobExecution.getEndTime());
        if (updated == 0) {
            log.info(
                    "Status update skipped for batchId={} — another instance already transitioned"
                            + " from {} (attempted: {} → {})",
                    batchId,
                    expectedStatus,
                    expectedStatus,
                    newStatus);
        }
        return updated;
    }

    /**
     * Releases the leader lock for the location phase if it is still held by the current thread.
     *
     * <p>Called on FAILED and STOPPED paths to ensure the lock does not stay held for its full
     * lease duration when the job exits before {@link
     * com.hmg.ipmap.ingestion.file.job.tasklet.MarkLocationPhaseCompletedTasklet} has a chance to
     * run.
     */
    private void releaseLockIfHeld() {
        Long batchId = jobParameter.getBatchId();
        if (batchId == null) {
            return;
        }
        distributedLockService.unlock(LocationCoordinationTasklet.LOCK_KEY_PREFIX + batchId);
        log.warn(
                "Released location phase lock after non-successful job exit for batchId={}",
                batchId);
    }

    private void printSeparator() {
        log.info("====================================");
    }
}
