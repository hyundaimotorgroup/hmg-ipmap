package com.hmg.ipmap.ingestion.file.job.tasklet;

import com.hmg.ipmap.common.redis.DistributedLockService;
import com.hmg.ipmap.ingestion.file.job.JobParameter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signals that the location phase is complete so that follower instances can proceed.
 *
 * <p>Sets the {@code batch:location-phase:done} flag in Redis (with a 6-hour TTL) before releasing
 * the leader lock. The flag is written first so that followers polling for it will never see the
 * lock released without the flag being present.
 *
 * <p>This tasklet runs as the final step of the leader's location phase flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkLocationPhaseCompletedTasklet implements Tasklet {

    private final RedissonClient redissonClient;
    private final DistributedLockService distributedLockService;
    private final JobParameter jobParameter;

    @Value("${app.ingestion.location-phase.done-key-ttl:6h}")
    private Duration doneKeyTtl;

    @Override
    public RepeatStatus execute(
            @NonNull StepContribution contribution, @NonNull ChunkContext chunkContext) {
        Long batchId = jobParameter.getBatchId();

        // Write done flag BEFORE releasing the lock so followers polling for it always see it
        RBucket<String> doneBucket =
                redissonClient.getBucket(LocationCoordinationTasklet.DONE_KEY_PREFIX + batchId);
        doneBucket.set("done", doneKeyTtl);
        log.info(
                "Location phase marked as completed for batch {} (flag TTL: {})",
                batchId,
                doneKeyTtl);

        distributedLockService.unlock(LocationCoordinationTasklet.LOCK_KEY_PREFIX + batchId);
        log.info("Released location phase lock");

        return RepeatStatus.FINISHED;
    }
}
