package com.hmg.ipmap.ingestion.file.job.event;

/**
 * Publishes a batch job start event to all running instances via Redis pub/sub.
 *
 * <p>Each instance subscribed via {@link BatchJobEventSubscriber} will receive the event and
 * independently launch its own Spring Batch execution for the same batch.
 */
public interface BatchJobEventPublisher {

    String CHANNEL = "batch:job:start";

    /**
     * Publishes a job start event to the {@value #CHANNEL} pub/sub channel.
     *
     * @param jobId the unique identifier of the batch job to start
     * @param userId the ID of the user who triggered the job
     */
    void publish(String jobId, Long userId);
}
