package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.ipmapping.dto.IpSpanProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link IpSpanEntity}. */
public interface IpSpanRepository extends JpaRepository<IpSpanEntity, Long> {

    /**
     * Deletes all IP span records associated with the given {@code source} IP mapping.
     *
     * @param source the parent IP mapping whose spans should be removed
     */
    void deleteAllByIpMapping(IpMappingEntity source);

    /**
     * Finds IP spans for a specific users
     *
     * @param ip the IP address as a long value
     * @param userIds the list of user IDs (client or/and sub_client)
     * @return List of matching IP spans
     */
    @Query(
            value =
                    """
                SELECT ise.id, ise.ip_mapping_id AS ipMappingId, ise.scope, ise.created_at AS createdAt
                FROM ip_span ise
                WHERE ise.ip_range @> CAST(:ip AS bigint)
                  AND ise.valid_period >= NOW()
                  AND ise.user_id IN :userIds
                """,
            nativeQuery = true)
    List<IpSpanProjection> findAllScopeByIpAndUserId(
            @Param("ip") Long ip, @Param("userIds") List<Long> userIds);
}
