package com.hmg.ipmap.ingestion.file.projection;

import java.time.LocalDateTime;

public interface BatchStatusProjection {
    String getBatchId();

    String getJobId();

    String getBatchStatus();

    Integer getTotalFileZip();

    Integer getTotalPercentage();

    LocalDateTime getStatedAt();

    LocalDateTime getFinishedAt();
}
