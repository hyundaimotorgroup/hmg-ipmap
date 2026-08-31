package com.hmg.ipmap.location;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Implementation of {@link LocationRepositoryCustom} that uses JPA {@code EntityManager} for
 * batched merge operations and {@code NamedParameterJdbcTemplate} for native SQL updates.
 *
 * <p>All operations flush and clear the persistence context in chunks of {@link #batchSize} to
 * bound memory pressure during large imports.
 */
@Repository
@RequiredArgsConstructor
public class LocationRepositoryCustomImpl implements LocationRepositoryCustom {

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${app.ingestion.location.chunk-size:500}")
    private int batchSize;

    @PostConstruct
    void validateConfig() {
        Assert.isTrue(
                batchSize > 0, "app.ingestion.location.chunk-size must be > 0, got: " + batchSize);
    }

    @Transactional
    @Override
    public List<LocationEntity> mergeAllLocations(Collection<LocationEntity> locations) {
        return mergeAll(locations);
    }

    /**
     * Merges (INSERT or UPDATE) city entities in batch and immediately sets their {@code path_ids}
     * within the same transaction.
     *
     * <p>City {@code path_ids} cannot be computed in Java before the INSERT because the id is
     * database-generated. A native SQL UPDATE derives it directly from the parent row that was
     * already flushed earlier in the same transaction, so it is visible to this UPDATE without
     * needing a separate commit.
     *
     * <p>Both operations share the same {@code @Transactional} boundary: if either fails the whole
     * unit rolls back atomically, preventing cities from being persisted with a {@code NULL
     * path_ids}.
     *
     * @param locations the city entities to merge
     * @return list of managed city entities with {@code path_ids} set
     */
    @Transactional
    @Override
    public List<LocationEntity> saveAllCityLocations(List<LocationEntity> locations) {
        if (locations.isEmpty()) {
            return List.of();
        }
        List<LocationEntity> result = mergeAll(locations);
        setPathIdsFromParent(result.stream().map(LocationEntity::getId).toList());
        return result;
    }

    /**
     * Upserts all location name entities using PostgreSQL {@code INSERT … ON CONFLICT DO UPDATE}.
     *
     * <p>Avoids the per-row {@code SELECT} that {@code EntityManager.merge()} would issue for
     * composite-key entities. Each chunk is sent as a single JDBC batch round-trip. On conflict
     * ({@code location_id + locale_code} already exists) only {@code name} and {@code updated_at}
     * are refreshed; {@code created_at} and {@code created_by} are left untouched.
     */
    @Transactional
    @Override
    public void upsertAllLocationNames(List<LocationNameEntity> names, Long executorUserId) {
        if (names.isEmpty()) {
            return;
        }

        // Sort by (location_id, locale_code) to guarantee a consistent lock acquisition order
        // across all concurrent instances. Without this, two instances processing overlapping
        // rows in different orders cause an AB/BA deadlock on the unique index.
        List<LocationNameEntity> sorted =
                names.stream()
                        .sorted(
                                Comparator.comparingLong(
                                                (LocationNameEntity n) ->
                                                        n.getLocationNameId().getLocationId())
                                        .thenComparing(n -> n.getLocationNameId().getLocaleCode()))
                        .toList();

        String sql =
                """
                INSERT INTO location_name
                    (location_id, locale_code, name, created_at, updated_at, created_by, updated_by)
                VALUES
                    (:locationId, :localeCode, :name, NOW(), NOW(), :executorUserId, :executorUserId)
                ON CONFLICT (location_id, locale_code)
                DO UPDATE SET
                    name       = EXCLUDED.name,
                    updated_at = NOW(),
                    updated_by = EXCLUDED.updated_by
                """;

        for (int start = 0; start < sorted.size(); start += batchSize) {
            List<LocationNameEntity> chunk =
                    sorted.subList(start, Math.min(start + batchSize, sorted.size()));
            SqlParameterSource[] params =
                    chunk.stream()
                            .map(
                                    n ->
                                            new MapSqlParameterSource()
                                                    .addValue(
                                                            "locationId",
                                                            n.getLocationNameId().getLocationId())
                                                    .addValue(
                                                            "localeCode",
                                                            n.getLocationNameId().getLocaleCode())
                                                    .addValue("name", n.getName())
                                                    .addValue("executorUserId", executorUserId))
                            .toArray(SqlParameterSource[]::new);
            namedParameterJdbcTemplate.batchUpdate(sql, params);
        }
    }

    /**
     * Merges all entities using the EntityManager in {@link #batchSize}-flushed chunks and returns
     * the managed instances carrying database-generated IDs.
     *
     * @param locations the entities to merge
     * @return list of managed entities
     */
    private List<LocationEntity> mergeAll(Collection<LocationEntity> locations) {
        List<LocationEntity> result = new ArrayList<>(locations.size());
        int i = 0;
        for (LocationEntity location : locations) {
            result.add(entityManager.merge(location));
            if (++i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    /**
     * Updates the {@code path_ids} ltree column for the given location IDs using a native SQL
     * UPDATE that derives the path from the already-flushed parent row.
     *
     * @param locationIds the IDs of the city locations whose {@code path_ids} should be updated
     */
    private void setPathIdsFromParent(List<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return;
        }
        String sql =
                """
                UPDATE location l
                SET path_ids = (p.path_ids::text || '.' || l.id::text)::ltree
                FROM location p
                WHERE l.parent_id = p.id
                  AND l.id IN (:ids)
                """;
        namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("ids", locationIds));
    }
}
