package com.hmg.ipmap.location;

import java.util.Collection;
import java.util.List;

/**
 * Custom repository contract for bulk-merge and batch operations on location and location-name
 * entities.
 *
 * <p>Location and city entities use {@link jakarta.persistence.EntityManager} merge with
 * configurable batch flushing. Location name entities use a native PostgreSQL UPSERT to avoid the
 * per-row SELECT that {@code EntityManager.merge()} issues for composite-key entities.
 */
public interface LocationRepositoryCustom {

    /**
     * Merges location entities in batch and returns the managed instances.
     *
     * <p>Delegates to {@link jakarta.persistence.EntityManager#merge}, which issues an UPDATE when
     * the entity already exists in the database (non-null ID) and an INSERT when it does not. All
     * callers are expected to pass entities that were previously fetched from the database.
     *
     * @param locations the location entities to merge
     * @return list of managed entities
     */
    List<LocationEntity> mergeAllLocations(Collection<LocationEntity> locations);

    /**
     * Merges city entities in batch and immediately derives and sets their {@code path_ids} via a
     * native SQL UPDATE within the same transaction.
     *
     * @param locations the city entities to merge
     * @return list of managed city entities with {@code path_ids} set
     */
    List<LocationEntity> saveAllCityLocations(List<LocationEntity> locations);

    /**
     * Upserts all location name entities using a single PostgreSQL {@code INSERT … ON CONFLICT DO
     * UPDATE} statement per batch chunk.
     *
     * <p>Eliminates the per-row {@code SELECT} that {@code EntityManager.merge()} would otherwise
     * issue for entities with a composite primary key. Rows that do not yet exist are inserted;
     * rows that already exist have their {@code name} and {@code updated_at} refreshed in-place.
     *
     * @param names location name entities to upsert
     * @param executorUserId ID of the batch-executing user, written to {@code created_by} / {@code
     *     updated_by}
     */
    void upsertAllLocationNames(List<LocationNameEntity> names, Long executorUserId);
}
