package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;
import com.hmg.ipmap.ingestion.file.job.model.LocationName;
import java.util.List;
import java.util.Set;

/** Registers location items in hierarchy order (continent → country → xxx → city). */
public interface LocationIngestionService<T extends BaseLocation> {

    /**
     * Registers a batch of location items in hierarchy order
     *
     * @param locations the location items to register in this chunk
     */
    void registerLocation(List<T> locations);

    /**
     * Registers location name translations for a batch of {@link LocationName} entries.
     *
     * @param items the set of location name items to register
     */
    void registerLocationName(Set<LocationName> items);
}
