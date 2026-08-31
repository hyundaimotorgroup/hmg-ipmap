package com.hmg.ipmap.ingestion.file.job.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

/**
 * Publishes a batch job start event to all running instances via Redis pub/sub.
 *
 * <p>Each instance subscribed via {@link BatchJobEventSubscriber} will receive the event and
 * independently launch its own Spring Batch execution for the same batch. Work is distributed
 * across instances automatically through the {@code claimForProcess} SKIP LOCKED mechanism.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobEventPublisherImpl implements BatchJobEventPublisher {

    private final RedissonClient redissonClient;

    @Override
    public void publish(String jobId, Long userId) {
        RTopic topic = redissonClient.getTopic(CHANNEL, StringCodec.INSTANCE);
        long instanceCount = topic.publish(jobId + "|" + userId);
        log.info(
                "Published job start event for jobId={} — delivered to {} instance(s)",
                jobId,
                instanceCount);
    }
}
