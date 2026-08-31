package com.hmg.ipmap.ingestion.file.job.event;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.ingestion.file.AsyncJobRunner;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * Subscribes to batch job start events and triggers a local Spring Batch execution on this
 * instance.
 *
 * <p>All running instances subscribe to the same Redis channel. When any instance publishes a job
 * start event, every instance (including the publisher) receives it and independently runs the same
 * batch job. Work is distributed between instances through the {@code claimForProcess} SKIP LOCKED
 * mechanism in {@code BatchFileDetailRepositoryCustomImpl}.
 *
 * <p>A minimal {@link UserContext} carrying only the {@code userId} is set on the Redisson callback
 * thread before dispatching to the async executor. {@link
 * com.hmg.ipmap.common.context.UserContextTaskDecorator} propagates it to the executor thread,
 * making {@code userId} available to batch writers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchJobEventSubscriber {

    private final RedissonClient redissonClient;
    private final AsyncJobRunner asyncJobRunner;

    /**
     * Registers a listener on the {@link BatchJobEventPublisher#CHANNEL} Redis pub/sub topic.
     *
     * <p>Called automatically after bean construction. For each received message, parses the {@code
     * jobId|userId} payload, sets a minimal {@link com.hmg.ipmap.common.context.UserContext} on the
     * current thread, and delegates to {@link AsyncJobRunner#run} for asynchronous execution. The
     * user context is always cleared in a {@code finally} block.
     */
    @PostConstruct
    public void subscribe() {
        RTopic topic =
                redissonClient.getTopic(BatchJobEventPublisher.CHANNEL, StringCodec.INSTANCE);
        topic.addListener(
                String.class,
                (channel, message) -> {
                    if (message == null) {
                        log.warn("Received null message on batch job start channel — ignoring");
                        return;
                    }
                    log.info("Received batch job start event: {}", message);
                    String[] parts = message.split("\\|");
                    if (parts.length != 2) {
                        log.error("Malformed batch job start event payload: {}", message);
                        return;
                    }
                    String jobId = parts[0];
                    if (!NumberUtils.isParsable(parts[1])) {
                        log.error("User id is null or not a number {}", parts[1]);
                        return;
                    }
                    Long userId = Long.parseLong(parts[1]);

                    // Provide a minimal UserContext so batch writers can resolve userId.
                    // UserContextTaskDecorator captures and propagates it to the async thread.
                    UserContextHolder.set(
                            new UserContext(userId, null, null, null, null, null, null));
                    try {
                        asyncJobRunner.run(jobId, userId);
                    } finally {
                        UserContextHolder.clear();
                    }
                });
        log.info(
                "Subscribed to batch job start events on channel: {}",
                BatchJobEventPublisher.CHANNEL);
    }
}
