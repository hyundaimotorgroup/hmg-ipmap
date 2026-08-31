package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import java.util.List;
import java.util.Optional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;

public interface IpMappingService {
    /**
     * Returns a paginated list of IP mappings filtered by the authenticated user's scope.
     *
     * @param req pagination parameters; defaults are applied if {@code null}
     * @return paginated response of {@link IpMappingResponseDto} records
     */
    @Transactional(readOnly = true)
    PaginationResponse<IpMappingResponseDto> searchWithPagination(
            @ModelAttribute PaginationRequest req);

    /**
     * Returns the IP mapping with the given {@code id}, enforcing ownership access checks.
     *
     * @param id the IP mapping identifier
     * @return the matching {@link IpMappingResponseDto}
     * @throws NotFoundException if no mapping exists with that id
     */
    @Transactional(readOnly = true)
    IpMappingResponseDto searchById(Long id);

    /**
     * Creates a new IP mapping and its derived IP spans, resolving and persisting the associated
     * location hierarchy.
     *
     * <p>Entity construction (notation-type determination, location resolution, field population)
     * is delegated to {@link IpMappingFactory#buildForCreate}.
     *
     * @param ipMappingRequestDto the request containing the IP notation and location data
     * @return the created {@link IpMappingResponseDto}
     * @throws NotFoundException if the current user or location cannot be resolved
     */
    @Transactional
    IpMappingResponseDto create(IpMappingRequestDto ipMappingRequestDto);

    /**
     * Updates an existing IP mapping and rebuilds its IP spans, enforcing ownership checks.
     *
     * <p>Field updates (notation-type determination, location resolution) are delegated to {@link
     * IpMappingFactory#applyUpdates}.
     *
     * @param id the IP mapping identifier
     * @param ipMappingRequestDto the updated request data
     * @return the updated {@link IpMappingResponseDto}
     * @throws NotFoundException if no mapping exists with that id
     */
    @Transactional
    IpMappingResponseDto update(Long id, IpMappingRequestDto ipMappingRequestDto);

    /**
     * Deletes the IP mapping with the given {@code id} along with its associated IP spans and
     * attributes, enforcing ownership checks.
     *
     * @param id the IP mapping identifier
     * @throws NotFoundException if no mapping exists with that id
     */
    @Transactional
    void delete(Long id);

    /**
     * Parses the IP notation of {@code ipMappingEntity} into a list of {@link IpSpanEntity} objects
     * without persisting them.
     *
     * @param ipMappingEntity the IP mapping entity whose notation should be expanded
     * @return list of unsaved IP span entities
     */
    List<IpSpanEntity> buildIpSpan(IpMappingEntity ipMappingEntity);

    /**
     * Fetches the IP mapping with the given {@code id} together with its attribute collection.
     *
     * @param id the IP mapping identifier
     * @return an {@link Optional} containing the entity, or empty if not found
     */
    @Transactional(readOnly = true)
    Optional<IpMappingEntity> findByIdWithAttributes(Long id);

    /**
     * Builds a response DTO for {@code ipMappingEntity} by loading the location hierarchy from the
     * database.
     *
     * @param ipMappingEntity the IP mapping entity
     * @return the populated {@link IpMappingResponseDto}
     */
    @Transactional(readOnly = true)
    IpMappingResponseDto getAndSetIpMappingResponseDto(IpMappingEntity ipMappingEntity);

    /**
     * Asynchronously rebuilds IP spans for all IP mappings in the id range [{@code startId}, {@code
     * endId}], processing records in chunks. Logs progress after each chunk and resumes from the
     * last cursor if interrupted.
     *
     * @param startId inclusive lower bound of the IP mapping id range
     * @param endId inclusive upper bound of the IP mapping id range
     */
    @Async
    void rebuildAllIpSpans(long startId, long endId);

    /**
     * Builds a response DTO for {@code ipMappingEntity} using a cache DTO as the source for
     * location and attribute data, avoiding additional database queries.
     *
     * @param ipMappingCache the cache DTO carrying location and attribute data
     * @param ipMappingEntity the IP mapping entity
     * @return the populated {@link IpMappingResponseDto}
     */
    IpMappingResponseDto getAndSetIpMappingResponseDto(
            IpMappingCacheDto ipMappingCache, IpMappingEntity ipMappingEntity);
}
