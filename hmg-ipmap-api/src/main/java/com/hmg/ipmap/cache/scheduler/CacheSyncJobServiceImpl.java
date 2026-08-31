package com.hmg.ipmap.cache.scheduler;

import com.hmg.ipmap.cache.constants.CacheSyncJobStatus;
import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import com.hmg.ipmap.cache.repository.CacheSyncJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheSyncJobServiceImpl implements CacheSyncJobService {

    private final CacheSyncJobRepository cacheSyncJobRepository;

    @Value("${cache-sync.job.batch-size:200}")
    private int batchSize;

    @Transactional
    @Override
    public List<CacheSyncJobEntity> fetchAndMarkJobsAsProcessing() {
        List<CacheSyncJobEntity> pendingJobs =
                cacheSyncJobRepository.findByStatusOrderByCreatedAtAsc(
                        CacheSyncJobStatus.PENDING, PageRequest.of(0, batchSize));

        if (pendingJobs.isEmpty()) {
            return pendingJobs;
        }

        log.info("Found {} pending cache sync jobs to process", pendingJobs.size());

        pendingJobs.forEach(job -> job.setStatus(CacheSyncJobStatus.PROCESSING));
        cacheSyncJobRepository.saveAllAndFlush(pendingJobs);

        log.info("Marked {} jobs as PROCESSING", pendingJobs.size());

        return pendingJobs;
    }
}
