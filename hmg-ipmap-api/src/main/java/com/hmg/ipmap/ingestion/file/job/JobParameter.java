package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserRepository;
import com.hmg.ipmap.user.exception.UserNotFoundException;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@JobScope
@Component
@RequiredArgsConstructor
public class JobParameter {

    public static final String PARAM_BATCH_ID = "batch.id";
    public static final String PARAM_USER_ID = "user.id";
    public static final String PARAM_RUN_DATE = "run.date";

    private final UserRepository userRepository;

    private UserEntity cachedExecutor;
    private Instant cachedRunDate;

    @Getter
    @Value("#{jobParameters['user.id']}")
    private Long userId;

    @Getter
    @Value("#{jobParameters['batch.id']}")
    private Long batchId;

    @Value("#{jobParameters['run.date']}")
    private String runDate;

    /**
     * Returns the {@link com.hmg.ipmap.user.UserEntity} that triggered the current batch job.
     *
     * <p>Resolves the {@code user.id} job parameter to a database record. Must only be called
     * within an active Spring Batch step execution where {@code user.id} has been set.
     *
     * @return the user entity for the job executor
     * @throws com.hmg.ipmap.user.exception.UserNotFoundException if {@code user.id} is not set or
     *     no user record exists for the given ID
     */
    public UserEntity getExecutor() {
        if (userId == null) {
            log.error("User id not found");
            throw new UserNotFoundException("User id is not set from job parameter");
        }
        if (cachedExecutor == null) {
            cachedExecutor =
                    userRepository
                            .findById(userId)
                            .orElseThrow(() -> new UserNotFoundException("User not found"));
        }
        return cachedExecutor;
    }

    /**
     * Returns the job run date as an {@link Instant} sourced directly from the {@code run.date} job
     * parameter. Falls back to {@link Instant#now()} if the parameter is not set.
     *
     * @return the job run date as an {@code Instant}
     */
    public Instant getJobRunDate() {
        if (cachedRunDate == null) {
            if (StringUtils.isBlank(runDate)) {
                log.warn("run.date is not set, using current time");
                cachedRunDate = Instant.now();
            } else {
                cachedRunDate = Instant.parse(runDate);
            }
        }
        return cachedRunDate;
    }
}
