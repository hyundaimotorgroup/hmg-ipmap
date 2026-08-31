package com.hmg.ipmap.ingestion.file;

import static com.hmg.ipmap.ingestion.file.job.JobParameter.PARAM_BATCH_ID;
import static com.hmg.ipmap.ingestion.file.job.JobParameter.PARAM_RUN_DATE;
import static com.hmg.ipmap.ingestion.file.job.JobParameter.PARAM_USER_ID;

import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.util.DateUtil;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobRunnerImpl implements AsyncJobRunner {

    private final JobOperator jobOperator;
    private final BatchRunRepository batchRunRepository;
    private final ApplicationContext applicationContext;

    private final Set<String> locallyRunningJobs = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isRunningLocally(String jobId) {
        return locallyRunningJobs.contains(jobId);
    }

    @Async("fileImportTaskExecutor")
    @Override
    public void run(String jobId, Long userId) {
        // Guard against duplicate dispatches (e.g. pub/sub and scheduler firing close together).
        if (!locallyRunningJobs.add(jobId)) {
            log.info(
                    "Job {} is already running on this instance — skipping duplicate dispatch",
                    jobId);
            return;
        }
        log.info("Running in thread: {}", Thread.currentThread().getName());

        BatchRunEntity batchRun = null;
        try {
            batchRun =
                    batchRunRepository
                            .findByJobId(jobId)
                            .orElseThrow(() -> new NotFoundException("Job not found"));

            Job job = applicationContext.getBean(batchRun.getJobName(), Job.class);

            Instant runDate = DateUtil.stringISOToInstant(jobId);
            JobParameters jobParameters =
                    new JobParametersBuilder()
                            .addString("run.id", UUID.randomUUID().toString())
                            .addString(PARAM_RUN_DATE, runDate.toString())
                            .addLong(PARAM_BATCH_ID, batchRun.getId())
                            .addLong(PARAM_USER_ID, userId)
                            .toJobParameters();

            jobOperator.start(job, jobParameters);
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("Job {} is already running in Spring Batch", jobId, e);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("Job {} instance is already complete in Spring Batch", jobId, e);
        } catch (InvalidJobParametersException | JobRestartException e) {
            log.error("Failed to launch job {}", jobId, e);
            markAsFailed(batchRun);
        } catch (CannotAcquireLockException e) {
            // PostgreSQL SQLSTATE 40001: this instance lost the race to create a JobExecution
            log.warn(
                    "Serialization conflict on BATCH_JOB_EXECUTION for job {} — "
                            + "this instance did not join; another instance is likely running it",
                    jobId,
                    e);
        } catch (Exception e) {
            log.error("Unexpected error running job {}", jobId, e);
            markAsFailed(batchRun);
        } finally {
            locallyRunningJobs.remove(jobId);
        }
    }

    private void markAsFailed(BatchRunEntity batchRun) {
        if (batchRun == null) {
            log.warn("Cannot mark job as FAILED: batchRun is null (job record was never loaded)");
            return;
        }
        // Use conditional update so concurrent instances don't race on the same row.
        // FAILED must also override a racing COMPLETED
        int updated =
                batchRunRepository.updateStatusConditionally(
                        batchRun.getJobId(),
                        BatchRunStatusEnum.IN_PROGRESS,
                        BatchRunStatusEnum.FAILED);
        if (updated == 0) {
            batchRunRepository.updateStatusConditionally(
                    batchRun.getJobId(), BatchRunStatusEnum.COMPLETED, BatchRunStatusEnum.FAILED);
        }
    }
}
