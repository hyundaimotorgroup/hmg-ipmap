package com.hmg.ipmap.ingestion.file.projection;

import java.time.LocalDateTime;

public interface BatchStatusDetailProjection {
    String getZipType();

    String getFileName();

    String getErrorMessage();

    String getStepName();

    String getStepStatus();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();

    String getExitMessage();

    int getLineCount();

    int getReadCount();

    int getSkipCount();
}
