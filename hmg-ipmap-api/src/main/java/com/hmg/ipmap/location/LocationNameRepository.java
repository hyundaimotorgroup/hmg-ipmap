package com.hmg.ipmap.location;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link LocationNameEntity}.
 *
 * <p>Provides derived queries and a bulk-delete method for managing localised name rows associated
 * with location records.
 */
public interface LocationNameRepository extends JpaRepository<LocationNameEntity, LocationNameId> {

    /**
     * Deletes all name rows that belong to the given location.
     *
     * @param locationId the id of the location whose names should be removed
     */
    void deleteByLocationId(Long locationId);

    /**
     * Deletes all name rows belonging to any of the given location IDs in a single bulk statement.
     *
     * @param ids the location IDs whose name rows should be removed
     */
    @Modifying
    @Query("DELETE FROM LocationNameEntity ln WHERE ln.location.id IN :ids")
    void deleteByLocationIdIn(@Param("ids") List<Long> ids);

    /**
     * Fetches name entities whose composite key matches any of the given {@link LocationNameId}
     * values.
     *
     * @param locationNameIds the composite keys to look up
     * @return list of matching name entities; never {@code null}
     */
    List<LocationNameEntity> findByLocationNameIdIn(List<LocationNameId> locationNameIds);

    /**
     * Fetches all name rows for the given set of location IDs.
     *
     * @param locationIds the location IDs whose names should be fetched
     * @return list of matching name entities; never {@code null}
     */
    List<LocationNameEntity> findAllByLocationIdIn(List<Long> locationIds);

    /**
     * Fetches all name rows for the given location.
     *
     * @param locationId the id of the location whose names should be fetched
     * @return list of name entities for the location; never {@code null}
     */
    List<LocationNameEntity> findAllByLocationId(Long locationId);
}
