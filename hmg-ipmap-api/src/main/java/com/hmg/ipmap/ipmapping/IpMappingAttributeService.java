package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.IpMappingAttributeRepository;
import java.util.List;

public interface IpMappingAttributeService {
    /**
     * Deletes all attribute entities associated with the given IP mapping.
     *
     * <p>Intended for use in the delete flow of {@link IpMappingServiceImpl} so that callers do not
     * need a direct dependency on {@link IpMappingAttributeRepository}.
     *
     * @param entity the IP mapping entity whose attributes should be removed
     */
    void deleteAllByIpMapping(IpMappingEntity entity);

    /**
     * Replaces all attribute entities for the given mapping.
     *
     * <p>Deletes the existing attributes, then builds and saves new ones derived from the location
     * sub-fields ({@code traits}, {@code postal}, {@code location}) in {@code request}. If no
     * attributes are present in the request nothing is saved.
     *
     * @param request the create/update request whose location attributes should be persisted
     * @param entity the IP mapping entity that owns the attributes
     */
    void replaceAttributes(IpMappingRequestDto request, IpMappingEntity entity);

    /**
     * Fetches all attribute entities for the given set of IP mapping IDs.
     *
     * <p>Used by the response-building path in {@link IpMappingServiceImpl} to batch-load
     * attributes without N+1 queries.
     *
     * @param ipMappingIds the IDs of the IP mappings whose attributes should be fetched
     * @return list of matching {@link IpMappingAttributeEntity} records
     */
    List<IpMappingAttributeEntity> fetchByIpMappingIds(List<Long> ipMappingIds);
}
