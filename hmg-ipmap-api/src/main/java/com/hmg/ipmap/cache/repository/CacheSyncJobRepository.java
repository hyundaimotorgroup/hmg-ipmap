package com.hmg.ipmap.cache.repository;

import com.hmg.ipmap.cache.entity.CacheSyncJobEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CacheSyncJobRepository extends JpaRepository<CacheSyncJobEntity, Long> {
    List<CacheSyncJobEntity> findByStatus(String status);

    List<CacheSyncJobEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
