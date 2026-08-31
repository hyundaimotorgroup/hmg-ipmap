package com.hmg.ipmap.ingestion.file.job.tasklet;

import com.hmg.ipmap.common.redis.DistributedLockService;
import com.hmg.ipmap.ingestion.file.job.JobParameter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Coordinates the location-phase execution across multiple application instances.
 *
 * <p>Only one instance (the <em>leader</em>) is allowed to run the three location steps (country,
 * city, enterprise). All other instances (<em>followers</em>) block here until the leader signals
 * completion, then proceed directly to the IP block phase.
 *
 * <p>The exit status of this step drives the job-flow decision:
 *
 * <ul>
 *   <li>{@code "LEADER"} — this instance acquired the lock and will run location steps.
 *   <li>{@code "FOLLOWER"} — another instance is (or was) running location steps; this instance
 *       waits until done, then skips to the IP block phase.
 * </ul>
 *
 * <h3>Crash recovery</h3>
 *
 * <p>The leader lock is set with a short TTL ({@code app.ingestion.location-phase.lock-ttl},
 * default 30 min) and is renewed by {@link
 * com.hmg.ipmap.ingestion.file.job.listener.LocationPhaseLeaderHeartbeat} after every chunk as long
 * as the leader JVM is alive. If the leader JVM is killed mid-phase the heartbeat stops, the lock
 * expires within {@code lock-ttl}, and the next follower poll attempt will re-acquire the lock and
 * take over as the new leader — limiting the recovery window to at most {@code lock-ttl +
 * poll-interval} instead of the full {@code await-timeout}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationCoordinationTasklet implements Tasklet {

    public static final String LOCK_KEY_PREFIX = "batch:location-phase:";
    static final String DONE_KEY_PREFIX = "batch:location-phase:done:";

    private final RedissonClient redissonClient;
    private final JobParameter jobParameter;
    private final DistributedLockService distributedLockService;

    @Value("${app.ingestion.location-phase.await-timeout:4h}")
    private Duration awaitTimeout;

    @Value("${app.ingestion.location-phase.poll-interval:5s}")
    private Duration pollInterval;

    /**
     * TTL applied to the leader lock. Must be long enough to survive at least one chunk of location
     * data. {@link com.hmg.ipmap.ingestion.file.job.listener.LocationPhaseLeaderHeartbeat} renews
     * it after every chunk so the actual hold duration is unbounded.
     */
    @Value("${app.ingestion.location-phase.lock-ttl:30s}")
    private Duration lockTtl;

    @Override
    public RepeatStatus execute(
            @NonNull StepContribution contribution, @NonNull ChunkContext chunkContext)
            throws InterruptedException {
        Long batchId = jobParameter.getBatchId();
        String doneKey = DONE_KEY_PREFIX + batchId;
        String lockKey = LOCK_KEY_PREFIX + batchId;

        RBucket<String> doneBucket = redissonClient.getBucket(doneKey);

        // Location phase was already completed by a previous run — skip it
        if (doneBucket.isExists()) {
            log.info("Location phase already completed — acting as follower");
            contribution.setExitStatus(new ExitStatus("FOLLOWER"));
            return RepeatStatus.FINISHED;
        }

        // Try to become the leader (single non-blocking attempt).
        // The lock TTL is intentionally short; LocationPhaseLeaderHeartbeat renews it every chunk.
        if (distributedLockService.tryLock(lockKey, Duration.ofMillis(100), lockTtl)) {
            log.info("Acquired location phase lock — acting as leader (lease: {})", lockTtl);
            contribution.setExitStatus(new ExitStatus("LEADER"));
            return RepeatStatus.FINISHED;
        }

        // Another instance holds the lock — poll until it signals done or the lock expires.
        log.info("Location phase is running on another instance — waiting for completion");
        long deadline = System.currentTimeMillis() + awaitTimeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            //noinspection BusyWait
            Thread.sleep(pollInterval.toMillis());

            if (doneBucket.isExists()) {
                log.info("Location phase completed by another instance — acting as follower");
                contribution.setExitStatus(new ExitStatus("FOLLOWER"));
                return RepeatStatus.FINISHED;
            }

            // The leader's heartbeat stops when its JVM dies. Once the short-lived lock expires,
            // the first follower to win tryLock here takes over as the new leader.
            if (distributedLockService.tryLock(lockKey, Duration.ofMillis(100), lockTtl)) {
                log.warn(
                        "batchId={} leader lock expired — this instance taking over as leader",
                        batchId);
                contribution.setExitStatus(new ExitStatus("LEADER"));
                return RepeatStatus.FINISHED;
            }
        }

        throw new IllegalStateException(
                "Timed out waiting for location phase to complete after " + awaitTimeout);
    }
}
