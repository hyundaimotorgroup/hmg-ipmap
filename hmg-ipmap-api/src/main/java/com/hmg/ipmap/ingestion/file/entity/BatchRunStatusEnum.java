package com.hmg.ipmap.ingestion.file.entity;

public enum BatchRunStatusEnum {
    RECEIVED,
    UPLOADING,
    READY,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELED;

    public boolean isExecuted() {
        return this == IN_PROGRESS || this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
