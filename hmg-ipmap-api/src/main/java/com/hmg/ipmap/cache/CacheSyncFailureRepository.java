package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.entity.CacheSyncFailureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CacheSyncFailureRepository extends JpaRepository<CacheSyncFailureEntity, Long> {}
