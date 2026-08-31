package com.hmg.ipmap.location;

import com.hmg.ipmap.ipmapping.IpMappingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link IpMappingAttributeEntity}.
 *
 * <p>Provides derived queries and a bulk-delete method for managing attribute blocks associated
 * with IP mapping records.
 */
public interface IpMappingAttributeRepository
        extends JpaRepository<IpMappingAttributeEntity, Long> {

    /**
     * Returns all attribute blocks associated with the given IP mapping.
     *
     * @param ipMappingEntity the owning IP mapping entity
     * @return list of matching attribute entities; never {@code null}
     */
    List<IpMappingAttributeEntity> findAllByIpMapping(IpMappingEntity ipMappingEntity);

    /**
     * Deletes all attribute blocks belonging to the given IP mapping.
     *
     * @param source the IP mapping entity whose attribute blocks should be removed
     */
    void deleteAllByIpMapping(IpMappingEntity source);

    /**
     * Returns all attribute blocks for any of the given IP mapping IDs.
     *
     * @param ipMappingIds the list of IP mapping IDs to query
     * @return list of matching attribute entities; never {@code null}
     */
    List<IpMappingAttributeEntity> findAllByIpMappingIdIn(List<Long> ipMappingIds);
}
