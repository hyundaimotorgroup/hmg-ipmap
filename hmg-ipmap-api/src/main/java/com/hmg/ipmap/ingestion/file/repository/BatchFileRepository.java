package com.hmg.ipmap.ingestion.file.repository;

import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFileRepository extends JpaRepository<BatchFileEntity, Long> {
    List<BatchFileEntity> findAllByBatchFileZip(BatchFileZipEntity batchFileZip);

    List<BatchFileEntity> findAllByBatchRunIdAndFileType(Long batchRunId, String fileType);
}
