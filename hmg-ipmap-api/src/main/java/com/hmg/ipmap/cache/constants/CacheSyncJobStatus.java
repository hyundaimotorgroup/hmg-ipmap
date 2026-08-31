package com.hmg.ipmap.cache.constants;

public final class CacheSyncJobStatus {

    private CacheSyncJobStatus() {
        // Private constructor to prevent instantiation
    }

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
}
