package com.hmg.ipmap.ingestion.file.job.listener;

import com.hmg.ipmap.common.redis.DistributedLockService;
import com.hmg.ipmap.ingestion.file.job.JobParameter;
import com.hmg.ipmap.ingestion.file.job.tasklet.LocationCoordinationTasklet;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renews the leader lock after every chunk so it does not expire during a long-running location
 * phase.
 *
 * <p>Registered as a {@link ChunkListener} on the three leader-only location steps ({@code
 * countryLocationStep}, {@code cityLocationStep}, {@code enterpriseLocationStep}) and also on the
 * three location-name steps ({@code countryLocationNamesStep}, {@code cityLocationNamesStep},
 * {@code enterpriseLocationNamesStep}) which run on all instances after the leader phase. On
 * follower instances, and on the leader after {@code markLocationPhaseCompletedStep} has released
 * the lock, this is a no-op — {@link DistributedLockService#renewLock} returns {@code false}
 * immediately when the current thread holds no lock token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationPhaseLeaderHeartbeat implements ChunkListener<Object, Object> {

    private final DistributedLockService distributedLockService;
    private final JobParameter jobParameter;

    @Value("${app.ingestion.location-phase.lock-ttl:30s}")
    private Duration lockTtl;

    @Override
    public void afterChunk(Chunk<Object> chunk) {
        Long batchId = jobParameter.getBatchId();
        if (batchId == null) {
            log.error(
                    "batch.id from job execution parameter is not set! failed to renew location phase leader");
            return;
        }
        String lockKey = LocationCoordinationTasklet.LOCK_KEY_PREFIX + batchId;
        boolean renewed = distributedLockService.renewLock(lockKey, lockTtl);
        if (renewed) {
            log.debug(
                    "Renewed location phase leader lock for batchId={} (ttl={})", batchId, lockTtl);
        }
    }
}
