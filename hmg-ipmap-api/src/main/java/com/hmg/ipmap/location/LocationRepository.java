package com.hmg.ipmap.location;

import com.hmg.ipmap.common.enums.Scope;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link LocationEntity} with custom recursive hierarchy queries.
 *
 * <p>Extends {@link LocationRepositoryCustom} for batch merge operations and provides recursive
 * CTE-based queries for ancestor traversal and ltree-based hierarchy lookups.
 */
public interface LocationRepository
        extends JpaRepository<LocationEntity, Long>, LocationRepositoryCustom {

    /**
     * Walks the location tree upward from all given leaf IDs and returns the deduplicated set of
     * ancestor entities for the entire batch.
     *
     * @param childIds the leaf location IDs to start traversal from
     * @return deduplicated list of ancestor entities for all supplied leaves
     */
    @Query(
            value =
                    """
    WITH RECURSIVE breadcrumb AS (
        SELECT l.*
        FROM location l
        WHERE l.id IN (:childIds)
        UNION ALL
        SELECT p.*
        FROM location p
        INNER JOIN breadcrumb b ON p.id = b.parent_id
    )
    SELECT DISTINCT * FROM breadcrumb
    """,
            nativeQuery = true)
    List<LocationEntity> findLocationRecursiveBatch(@Param("childIds") List<Long> childIds);

    /**
     * Returns the given location and all of its descendants in a flat list.
     *
     * @param startId the id of the root location to start from
     * @return flat list containing the start location and every descendant
     */
    @Query(
            value =
                    "WITH RECURSIVE parent_and_children AS ("
                            + "    SELECT * FROM location WHERE id = :startId"
                            + "    UNION ALL"
                            + "    SELECT l.* FROM location l"
                            + "    INNER JOIN parent_and_children b ON l.parent_id = b.id"
                            + ")"
                            + "SELECT * FROM parent_and_children;",
            nativeQuery = true)
    List<LocationEntity> findLocationParentAndChildren(@Param("startId") Long startId);

    /**
     * Returns all leaf-level location nodes (locations that have no children).
     *
     * @return list of leaf {@link LocationEntity} objects; never {@code null}
     */
    @Query(
            value =
                    "WITH RECURSIVE last_children AS ("
                            + "    SELECT * FROM location WHERE id = :startId"
                            + "    UNION ALL"
                            + "    SELECT l.* FROM location l"
                            + "    INNER JOIN last_children b ON l.parent_id = b.id"
                            + ")"
                            + "SELECT lc.* FROM last_children lc WHERE NOT EXISTS ("
                            + " SELECT 1 FROM location l WHERE l.parent_id = lc.id"
                            + ");",
            nativeQuery = true)
    List<LocationEntity> findByIdIsNotNullAndParentIdIsNull();

    Optional<LocationEntity> findByLocationCodeAndLocationLevel(
            String locationCode, String locationLevel);

    Page<LocationEntity> findAll(Specification<LocationEntity> spec, Pageable pageable);

    List<LocationEntity> findByScopeEqualsAndGeonameIdIn(Scope scope, Collection<Long> geonameIds);

    List<LocationEntity> findByParent(LocationEntity parent);

    Optional<LocationEntity> findLocationByGeonameIdAndScope(Long geonameId, Scope scope);

    List<LocationEntity> findByScopeEqualsAndLocationCodeIn(
            Scope scope, Collection<String> locationCodes);

    List<LocationEntity> findLocationByGeonameIdInAndUserId(
            Collection<Long> geonameIds, Long userId);

    Optional<LocationEntity> findLocationByGeonameIdAndUserId(Long geonameId, Long userId);

    List<LocationEntity> findLocationByGeonameIdInAndScope(
            Collection<Long> geonameIds, Scope scope);

    /**
     * Returns the IDs of all ancestors (including the node itself) by using the {@code path_ids}
     * ltree containment operator.
     *
     * @param childId the id of the leaf node
     * @return list of ancestor IDs including the node itself
     */
    @Query(
            value =
                    """
        SELECT l.id
        FROM location l
        JOIN location leaf ON leaf.id = :childId
        WHERE l.path_ids @> leaf.path_ids
        """,
            nativeQuery = true)
    List<Long> findHierarchyIds(@Param("childId") Long childId);

    /**
     * Loads a batch of location entities by their IDs, eagerly fetching their localised name
     * collections in a single query.
     *
     * @param ids the list of location IDs to fetch
     * @return list of entities with names eagerly loaded; never {@code null}
     */
    @Query(
            """
        SELECT DISTINCT le FROM LocationEntity le
        LEFT JOIN FETCH le.names
        WHERE le.id IN :ids
        """)
    List<LocationEntity> findAllWithNamesByIds(@Param("ids") List<Long> ids);

    /**
     * Returns the full ancestor hierarchy (including the node itself) with names eagerly loaded,
     * using the ltree-based {@link #findHierarchyIds} lookup.
     *
     * @param childId the id of the leaf node whose ancestors should be loaded
     * @return list of ancestor entities with names loaded; empty list if the node does not exist
     */
    default List<LocationEntity> findHierarchyWithNames(Long childId) {
        List<Long> ids = findHierarchyIds(childId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return findAllWithNamesByIds(ids);
    }
}
