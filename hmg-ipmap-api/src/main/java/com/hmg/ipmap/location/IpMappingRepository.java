package com.hmg.ipmap.location;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link com.hmg.ipmap.ipmapping.IpMappingEntity} with custom JPQL
 * queries.
 *
 * <p>Provides scope-filtered pagination, eager-loading projections, and chunk-based range queries
 * used during IP span rebuild operations.
 */
public interface IpMappingRepository extends JpaRepository<IpMappingEntity, Long> {

    /**
     * Returns the most recently created GLOBAL-scoped mapping for each of the given IP notations.
     *
     * @param ipNotations the set of IP notation strings to look up
     * @return list of the latest GLOBAL mappings, one per distinct notation
     */
    @Query(
            """
        SELECT im
        FROM IpMappingEntity im
        WHERE im.ipNotation IN :ipNotations
          AND im.scope = com.hmg.ipmap.common.enums.Scope.GLOBAL
          AND im.createdAt = (
            SELECT MAX(im2.createdAt)
            FROM IpMappingEntity im2
            WHERE im2.ipNotation = im.ipNotation
          )
        """)
    List<IpMappingEntity> findAllLatestByIpNotations(Set<String> ipNotations);

    /**
     * Returns all IP mappings that reference any of the given location IDs.
     *
     * @param locationIds the location IDs to query
     * @return list of matching IP mapping entities; never {@code null}
     */
    List<IpMappingEntity> findAllByLocationIdIn(Collection<Long> locationIds);

    /**
     * Returns a page of IP mappings filtered to the specified scope.
     *
     * @param scope the scope to filter by
     * @param pageable the pagination parameters
     * @return page of matching IP mapping entities
     */
    Page<IpMappingEntity> findAllByScope(Scope scope, Pageable pageable);

    /**
     * Fetches a single IP mapping by id, eagerly loading its attribute collection to avoid N+1
     * queries.
     *
     * @param id the IP mapping id
     * @return an {@link java.util.Optional} containing the entity with attributes, or empty if not
     *     found
     */
    @Query(
            """
        SELECT im
        FROM IpMappingEntity im
                LEFT JOIN FETCH im.attributes attributes
        WHERE im.id = :id
        """)
    Optional<IpMappingEntity> findByIdWithAttributes(@Param("id") Long id);

    /**
     * Fetches a page of IP mappings whose IDs fall within [{@code currentId}, {@code endId}],
     * ordered ascending, with the owning user eagerly loaded.
     *
     * @param currentId the inclusive lower bound of the id range
     * @param endId the inclusive upper bound of the id range
     * @param pageable the pagination parameters controlling chunk size
     * @return list of matching IP mapping entities with user eagerly loaded
     */
    @Query(
            """
        SELECT im
        FROM IpMappingEntity im
        JOIN FETCH im.user
        WHERE im.id >= :currentId AND im.id <= :endId
        ORDER BY im.id ASC
        """)
    List<IpMappingEntity> findWithUserInRange(
            @Param("currentId") Long currentId, @Param("endId") Long endId, Pageable pageable);

    /**
     * Returns all IP mappings whose notation matches any of the given values, with their associated
     * {@link com.hmg.ipmap.location.LocationEntity} eagerly loaded in a single JOIN query.
     *
     * @param ipNotations the IP notation strings to look up
     * @return list of matching IP mapping entities with location loaded
     */
    @Query(
            """
        SELECT im
        FROM IpMappingEntity im
        JOIN FETCH im.location
        WHERE im.ipNotation IN :ipNotations
        """)
    List<IpMappingEntity> findAllByIpNotationInWithLocation(
            @Param("ipNotations") Collection<String> ipNotations);
}
