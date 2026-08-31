package com.hmg.ipmap.ingestion.file;

public interface AsyncJobRunner {
    /**
     * Returns {@code true} if the given job is currently executing on this instance. Used by the
     * polling scheduler to avoid starting a duplicate execution on an already-busy instance.
     */
    boolean isRunningLocally(String jobId);

    /**
     * Launches the Spring Batch job for the given job ID asynchronously. If the job is already
     * running on this instance, the call is a no-op.
     *
     * @param jobId the unique identifier of the batch job to launch
     * @param userId the ID of the user who triggered the job, injected as a job parameter
     */
    void run(String jobId, Long userId);
}
