package com.hmg.ipmap.location;

import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import com.hmg.ipmap.location.enums.LocationLevel;
import com.hmg.ipmap.location.exception.LocationAlreadyExistException;
import com.hmg.ipmap.location.exception.LocationNotFoundException;
import com.hmg.ipmap.user.UserEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public interface LocationService {
    /**
     * Converts a {@link LocationEntity} and its name rows into a {@link LocationDto}.
     *
     * @param locationEntity the entity to convert
     * @param locationNameEntities the associated name rows; may be empty
     * @return a populated {@link LocationDto}
     */
    LocationDto toLocationDto(
            LocationEntity locationEntity, List<LocationNameEntity> locationNameEntities);

    /**
     * Deletes the location with the given id and its localised names, enforcing ownership and
     * preventing deletion of locations referenced by IP mappings or child locations.
     *
     * @param id the id of the location to delete
     * @throws LocationNotFoundException if no location exists with that id
     * @throws LocationAlreadyExistException if the location is still referenced by IP mappings or
     *     is a parent of other locations
     */
    void delete(Long id);

    /**
     * Loads the full ancestor hierarchy for the given location id and returns it as a structured
     * DTO.
     *
     * <p>Results are cached in the Caffeine cache keyed by location id.
     *
     * @param locationId the id of the leaf location to load
     * @return a {@link LocationResponseDto} populated with all ancestor tiers
     * @throws LocationNotFoundException if no location exists with that id
     */
    LocationResponseDto findLocationHierarchy(Long locationId);

    /**
     * Recursively populates a {@link LocationResponseDto} from a {@link LocationCacheDto} chain,
     * traversing parent links until the root is reached.
     *
     * @param cacheDto the cache DTO to map from; must not be {@code null}
     * @param response the response DTO to populate; a new instance is created if {@code null}
     * @return the fully populated {@link LocationResponseDto}
     */
    LocationResponseDto buildLocationDtoFromCache(
            LocationCacheDto cacheDto, LocationResponseDto response);

    /**
     * Loads the full ancestor hierarchy for the location with the given id.
     *
     * @param id the location id
     * @return a {@link LocationResponseDto} containing all ancestor tiers
     * @throws LocationNotFoundException if no location exists with that id
     */
    Map<String, LocationDto> findById(Long id);

    /**
     * Loads all root-level locations (those without a parent) and returns each with its full
     * descendant hierarchy.
     *
     * @return list of {@link LocationResponseDto} objects, one per root location
     */
    List<LocationResponseDto> findAll();

    /**
     * Returns a paginated list of all location records.
     *
     * @param req pagination parameters; default values are applied when {@code null}
     * @return paginated response of {@link LocationDto} records
     */
    PaginationResponse<LocationDto> searchWithPagination(PaginationRequest req);

    /**
     * Creates the location hierarchy described in the request for the authenticated user.
     *
     * @param allLocationMap the resolved location hierarchy to create
     * @return a {@link LocationResponseDto} containing the persisted hierarchy
     * @throws com.hmg.ipmap.common.exception.NotFoundException if the authenticated user cannot be
     *     resolved
     */
    Map<LocationLevel, LocationDto> create(Map<LocationLevel, LocationDto> allLocationMap);

    /**
     * Creates or retrieves the location hierarchy described in the request for the given user.
     *
     * <p>If all supplied locations are unchanged relative to stored records the existing hierarchy
     * is returned without additional writes. If only a geoname id is supplied, the existing record
     * is returned directly.
     *
     * @param user the user under whose account the locations are created
     * @param allLocationMap the resolved location hierarchy to create or look up
     * @param isFromLocationRequest {@code true} when the call originates from the location endpoint
     * @return a {@link LocationResponseDto} containing the resolved or persisted hierarchy
     */
    Map<LocationLevel, LocationDto> createLocation(
            UserEntity user,
            Map<LocationLevel, LocationDto> allLocationMap,
            boolean isFromLocationRequest);

    /**
     * Resolves a single location by geoname id using a user-type-aware fallback strategy.
     *
     * <p>SUB_CLIENT users cascade through their own records → parent user → GLOBAL scope; CLIENT
     * users cascade through their own records → GLOBAL scope; other types query only the current
     * user.
     *
     * @param geonameId the GeoNames identifier to look up
     * @param currentUser the authenticated user driving the fallback logic
     * @return an {@link Optional} containing the resolved entity, or empty if not found
     */
    Optional<LocationEntity> findLocationWithFallback(Long geonameId, UserEntity currentUser);

    /**
     * Checks whether the given location DTO matches the currently stored record.
     *
     * @param locationDto the candidate location DTO; returns {@code true} if {@code null}
     * @param isFromLocationRequest {@code true} when the DTO originates from a direct location
     *     request rather than an IP mapping request
     * @return {@code true} if the stored record matches the DTO (ignoring id), {@code false}
     *     otherwise
     */
    boolean isLocationUnchanged(LocationDto locationDto, boolean isFromLocationRequest);

    /**
     * Resolves multiple locations by geoname id using a user-type-aware fallback strategy.
     *
     * @param geonameIds the list of GeoNames identifiers to look up
     * @param currentUser the authenticated user driving the fallback logic
     * @return list of matching {@link LocationEntity} objects; may be empty
     */
    List<LocationEntity> findLocationByGeonameIdsWithFallback(
            List<Long> geonameIds, UserEntity currentUser);

    /**
     * Updates the location with the given id, replacing its attributes, geoname id, location code,
     * and localised names.
     *
     * @param id the id of the location to update
     * @param request the updated location data
     * @return the updated {@link LocationDto} with its id set
     * @throws LocationNotFoundException if no location exists with that id
     */
    @Transactional
    LocationDto update(Long id, LocationDto request);

    /**
     * Returns {@code true} if the incoming entity has meaningful changes compared to the existing
     * entity (different location code, geoname id, or attributes).
     *
     * @param existing the currently stored location entity
     * @param incoming the candidate entity built from the request DTO
     * @return {@code true} if at least one relevant field differs
     */
    boolean isUpdateNeeded(LocationEntity existing, LocationEntity incoming);

    /**
     * Builds the materialized {@code ltree} path of location IDs for a given location.
     *
     * <p>The path is a {@code .}-delimited {@code ltree} label path from the root down to the
     * current location, e.g. {@code "1.2.5"}. Because {@code ltree} labels must match {@code
     * [0-9]+}, hyphens in location IDs are replaced with underscores. If the parent has no {@code
     * pathIds} (e.g. a root-level continent), the method falls back to returning the sanitized
     * {@code id} alone.
     *
     * @param parent the direct parent entity, or {@code null} if this is a root location
     * @param id the id of the location whose path is being built
     * @return the full {@code ltree}-compatible materialised path, never {@code null}
     */
    String buildPathIds(LocationEntity parent, long id);
}
