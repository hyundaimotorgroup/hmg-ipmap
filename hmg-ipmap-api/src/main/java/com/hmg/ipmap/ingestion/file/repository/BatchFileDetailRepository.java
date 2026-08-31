package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.ingestion.file.entity.BatchFileDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFileDetailRepository
        extends JpaRepository<BatchFileDetailEntity, Long>, BatchFileDetailRepositoryCustom {}
