package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.location.LocationEntity;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/**
 * Step-level location cache for IP block ingestion.
 *
 * <p>During a batch step, {@link LocationEntity} objects are stable — the location phase completes
 * before the IP block phase begins, so a location resolved for geoname ID X in chunk 1 is identical
 * to the one needed in chunk 5000. Caching them here prevents repeated {@code
 * findByScopeEqualsAndGeonameIdIn} queries for geoname IDs already seen in earlier chunks.
 *
 * <p>Annotated with {@link StepScope} so Spring Batch creates a fresh instance at the start of each
 * step and destroys it when the step ends — no manual cache eviction is required.
 *
 * <p>Uses a {@link ConcurrentHashMap} to allow safe access if the step is ever configured with
 * parallel chunk processing, while remaining correct under the default single-threaded model.
 */
@Component
@StepScope
public class IpBlockIngestionStepCache {

    private final Map<Long, LocationEntity> locationCache = new ConcurrentHashMap<>();

    /**
     * Returns the subset of the requested geoname IDs that are not yet present in the cache. The
     * caller should fetch these from the database and populate the cache via {@link
     * #putLocation(Long, LocationEntity)}.
     */
    public Set<Long> getMissingGeonameIds(Set<Long> requested) {
        return requested.stream()
                .filter(id -> !locationCache.containsKey(id))
                .collect(Collectors.toSet());
    }

    public LocationEntity getLocation(Long geonameId) {
        return locationCache.get(geonameId);
    }

    public void putLocation(Long geonameId, LocationEntity location) {
        locationCache.put(geonameId, location);
    }
}
