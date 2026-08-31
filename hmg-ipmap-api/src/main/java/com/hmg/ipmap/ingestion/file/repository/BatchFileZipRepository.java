package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import com.hmg.ipmap.ingestion.file.enums.ZipStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFileZipRepository extends JpaRepository<BatchFileZipEntity, Long> {

    boolean existsByBatchRunIdAndZipTypeAndStatus(
            Long batchRunId, String zipType, ZipStatusEnum status);
}
