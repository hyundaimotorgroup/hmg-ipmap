package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;

/** Service responsible for cache management operations during batch processing. */
public interface IngestionCacheService {

    /**
     * Preloads the location and location-name caches for a location processing batch.
     *
     * @param context the location processing context whose caches will be populated
     */
    void preloadCache(LocationProcessingContext<? extends BaseLocation> context);

    /**
     * Preloads the location and IP mapping caches for an IP block processing batch.
     *
     * @param context the IP block processing context whose caches will be populated
     */
    void preloadCache(IpBlockProcessingContext context);
}
