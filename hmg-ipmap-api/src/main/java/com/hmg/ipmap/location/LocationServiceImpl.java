package com.hmg.ipmap.location;

import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.exception.GlobalException;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import com.hmg.ipmap.location.enums.LocationLevel;
import com.hmg.ipmap.location.exception.LocationAlreadyExistException;
import com.hmg.ipmap.location.exception.LocationNotFoundException;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserRepository;
import com.hmg.ipmap.user.UserService;
import com.hmg.ipmap.user.UserServiceImpl;
import io.micrometer.common.util.StringUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for location CRUD operations and location hierarchy resolution.
 *
 * <p>Supports creating, updating, deleting, and querying location hierarchies (continent → country
 * → subdivision → city). Enforces user-scoped access control and coordinates persistence of
 * localised names alongside location records.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final LocationNameRepository locationNameRepository;
    private final UserRepository userRepository;
    private final IpMappingRepository ipMappingRepository;
    private final UserService userService;
    private final LocationNameMapper locationNameMapper;

    private static final String LOCATION_NOT_FOUND_MSG_BY_ID = "Location with id %s not found";

    private static final String LOCATION_LEVEL_CONTINENT = "CONTINENT";
    private static final String LOCATION_LEVEL_COUNTRY = "COUNTRY";
    private static final String LOCATION_LEVEL_CITY = "CITY";
    private static final String CACHE_NAME_LOCATION_BY_ID = "locationById";
    private static final String CACHE_MANAGER_CAFFEINE = "caffeineCacheManager";

    /**
     * Converts the names map from a {@link LocationDto} into a list of {@link LocationNameEntity}
     * objects for the given location id.
     */
    private List<LocationNameEntity> getLocationNameEntities(
            LocationDto locationDtoRequest, Long id) {
        Map<String, String> namesMap = locationDtoRequest.getNames();
        if (namesMap != null && !namesMap.isEmpty()) {
            return namesMap.entrySet().stream()
                    .map(
                            map -> {
                                LocationNameEntity newAttr = new LocationNameEntity();
                                newAttr.setLocationNameId(new LocationNameId(id, map.getKey()));
                                newAttr.setName(map.getValue());
                                return newAttr;
                            })
                    .toList();
        } else {
            return List.of();
        }
    }

    @Override
    public LocationDto toLocationDto(
            LocationEntity locationEntity, List<LocationNameEntity> locationNameEntities) {

        LocationDto locationDto = new LocationDto();

        locationDto.setId(locationEntity.getId());
        locationDto.setLocationCode(locationEntity.getLocationCode());
        locationDto.setGeonameId(locationEntity.getGeonameId());
        locationDto.setAttributes(locationEntity.getAttributes());

        Map<String, String> names = new HashMap<>();
        locationNameEntities.forEach(
                locationNameEntity ->
                        names.put(
                                locationNameEntity.getLocationNameId().getLocaleCode(),
                                locationNameEntity.getName()));
        locationDto.setNames(names);

        return locationDto;
    }

    @CacheEvict(
            value = CACHE_NAME_LOCATION_BY_ID,
            allEntries = true,
            cacheManager = CACHE_MANAGER_CAFFEINE)
    @Transactional
    @Override
    public void delete(Long id) {

        log.debug("Starting delete operation for location id: {}", id);

        LocationEntity existing =
                locationRepository.findById(id).orElseThrow(LocationNotFoundException::new);

        log.debug("Found existing location: {}", existing);

        userService.checkUserAccess(UserContextHolder.get(), existing.getUser());

        List<LocationEntity> locationParentAndChildrenEntity =
                locationRepository.findLocationParentAndChildren(existing.getId());

        if (locationParentAndChildrenEntity == null || locationParentAndChildrenEntity.isEmpty()) {
            log.warn("No location parent and children found for id: {}", id);
            throw new LocationNotFoundException();
        }

        log.debug("Found {} location(s) in hierarchy", locationParentAndChildrenEntity.size());

        List<Long> locationIds =
                locationParentAndChildrenEntity.stream().map(LocationEntity::getId).toList();

        log.debug("Location IDs to be deleted: {}", locationIds);

        List<IpMappingEntity> ipMappingEntities =
                ipMappingRepository.findAllByLocationIdIn(locationIds);

        if (!ipMappingEntities.isEmpty()) {
            log.warn(
                    "Cannot delete location. Found {} IP mapping(s) using these locations",
                    ipMappingEntities.size());
            throw new LocationAlreadyExistException("Location being be used");
        }

        List<LocationEntity> usedParents = locationRepository.findByParent(existing);
        if (!usedParents.isEmpty()) {
            log.warn(
                    "Cannot delete location. Found {} location(s) using these locations",
                    usedParents.size());
            throw new LocationAlreadyExistException("Location being be used in hierarchy");
        }

        log.debug("Deleting location name by location ID {}", existing.getId());
        locationNameRepository.deleteByLocationId(existing.getId());

        log.debug("Deleting location by location ID {}", existing.getId());
        locationRepository.deleteById(existing.getId());
    }

    @Cacheable(
            value = CACHE_NAME_LOCATION_BY_ID,
            key = "#locationId",
            cacheManager = CACHE_MANAGER_CAFFEINE,
            sync = true)
    @Transactional(readOnly = true)
    @Override
    public LocationResponseDto findLocationHierarchy(Long locationId) {

        List<LocationEntity> locationWithNames =
                locationRepository.findHierarchyWithNames(locationId);

        if (CollectionUtils.isEmpty(locationWithNames)) {
            throw new LocationNotFoundException("Location with id " + locationId + " not found");
        }

        return buildLocationResponseDto(locationWithNames);
    }

    /**
     * Distributes a flat list of location entities across the hierarchy slots of a new {@link
     * LocationResponseDto}.
     */
    private LocationResponseDto buildLocationResponseDto(List<LocationEntity> hierarchy) {

        LocationResponseDto response = new LocationResponseDto();
        List<LocationDto> additionalLocations = new ArrayList<>();

        for (LocationEntity entity : hierarchy) {
            LocationDto dto = toLocationDto(entity, entity.getNames());

            switch (entity.getLocationLevel()) {
                case LOCATION_LEVEL_CONTINENT -> response.setContinent(dto);
                case LOCATION_LEVEL_COUNTRY -> response.setCountry(dto);
                case LOCATION_LEVEL_CITY -> response.setCity(dto);
                default -> additionalLocations.add(dto);
            }
        }

        if (!additionalLocations.isEmpty()) {
            response.setAdditionalLocations(additionalLocations);
        }

        return response;
    }

    @Override
    public LocationResponseDto buildLocationDtoFromCache(
            LocationCacheDto cacheDto, LocationResponseDto response) {
        if (response == null) {
            response = new LocationResponseDto();
        }

        LocationEntity locationEntity = locationMapper.cacheToEntity(cacheDto);
        locationEntity.setAttributes(cacheDto.getAttributeMap());
        locationEntity.setNames(
                cacheDto.getNameDtos().stream().map(locationNameMapper::cacheToEntity).toList());
        LocationDto locationDto = toLocationDto(locationEntity, locationEntity.getNames());

        switch (locationEntity.getLocationLevel()) {
            case LOCATION_LEVEL_CONTINENT -> response.setContinent(locationDto);
            case LOCATION_LEVEL_CITY -> response.setCity(locationDto);
            case LOCATION_LEVEL_COUNTRY -> response.setCountry(locationDto);
            default -> {
                if (response.getAdditionalLocations() == null) {
                    response.setAdditionalLocations(new ArrayList<>());
                }
                response.getAdditionalLocations().add(locationDto);
            }
        }

        // the latest LocationLevel is CONTINENT
        if (cacheDto.getParentDto() != null
                && !Objects.equals(locationEntity.getLocationLevel(), LOCATION_LEVEL_CONTINENT)) {
            buildLocationDtoFromCache(cacheDto.getParentDto(), response);
        }

        return response;
    }

    @Override
    public Map<String, LocationDto> findById(Long id) {

        LocationEntity existing =
                locationRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new LocationNotFoundException(
                                                String.format(LOCATION_NOT_FOUND_MSG_BY_ID, id)));

        return locationRepository.findHierarchyWithNames(existing.getId()).stream()
                .collect(
                        Collectors.toMap(
                                LocationEntity::getLocationLevel,
                                locationEntity ->
                                        toLocationDto(locationEntity, locationEntity.getNames())));
    }

    private Map<LocationLevel, LocationDto> findByGeonameIdAndTargetUserId(
            Long geonameId, Long targetUserId, Map<LocationLevel, LocationDto> allLocationMap) {

        LocationEntity existing =
                locationRepository
                        .findLocationByGeonameIdAndUserId(geonameId, targetUserId)
                        .orElseThrow(
                                () ->
                                        new LocationNotFoundException(
                                                "Location with geonameId "
                                                        + geonameId
                                                        + " and target User Id  "
                                                        + targetUserId
                                                        + " not found"));

        return locationRepository.findHierarchyWithNames(existing.getId()).stream()
                .collect(
                        Collectors.toMap(
                                entity ->
                                        allLocationMap.keySet().stream()
                                                .filter(
                                                        k ->
                                                                k.name()
                                                                        .equals(
                                                                                entity
                                                                                        .getLocationLevel()))
                                                .findFirst()
                                                .orElseThrow(),
                                entity -> toLocationDto(entity, entity.getNames())));
    }

    @Override
    public List<LocationResponseDto> findAll() {
        List<LocationResponseDto> locationResponseDtos = new ArrayList<>();

        List<LocationEntity> locationEntities =
                locationRepository.findByIdIsNotNullAndParentIdIsNull();

        for (LocationEntity locationEntity : locationEntities) {

            List<LocationEntity> locationParentAndChildrenEntities =
                    locationRepository.findLocationParentAndChildren(locationEntity.getId());

            locationResponseDtos.add(buildLocationResponseDto(locationParentAndChildrenEntities));
        }

        return locationResponseDtos;
    }

    @Override
    public PaginationResponse<LocationDto> searchWithPagination(PaginationRequest req) {

        int page = (req == null) ? 0 : Math.max(0, req.pageOrDefault());
        int size = (req == null) ? 10 : Math.max(1, req.sizeOrDefault());

        Pageable pageable = PageRequest.of(page, size);
        log.debug("pageable={}", pageable);
        Page<LocationEntity> locationEntities = locationRepository.findAll(pageable);

        Page<LocationDto> locationDtoPage = locationEntities.map(locationMapper::toLocationDto);

        return new PaginationResponse<>(
                locationDtoPage.getContent(),
                locationDtoPage.isLast(),
                locationDtoPage.getTotalElements(),
                locationDtoPage.getTotalPages(),
                locationDtoPage.isFirst(),
                locationDtoPage.getSize(),
                locationDtoPage.getNumber(),
                locationDtoPage.getNumberOfElements(),
                locationDtoPage.isEmpty());
    }

    @Transactional
    @Override
    public Map<LocationLevel, LocationDto> create(Map<LocationLevel, LocationDto> allLocationMap) {
        UserEntity currentUser =
                userRepository
                        .findById(UserContextHolder.get().id())
                        .orElseThrow(() -> new NotFoundException(UserServiceImpl.USER_NOT_FOUND));
        return createLocation(currentUser, allLocationMap, true);
    }

    @Override
    public Optional<LocationEntity> findLocationWithFallback(
            Long geonameId, UserEntity currentUser) {
        return switch (currentUser.getUserType()) {
            case SUB_CLIENT -> findLocationForSubClient(geonameId, currentUser);
            case CLIENT -> findLocationForClient(geonameId, currentUser);
            default ->
                    locationRepository.findLocationByGeonameIdAndUserId(
                            geonameId, currentUser.getId());
        };
    }

    /**
     * Resolves a location by geonameId for a CLIENT user, falling back to the GLOBAL scope when no
     * user-owned record is found.
     */
    private Optional<LocationEntity> findLocationForClient(Long geonameId, UserEntity currentUser) {
        // Try current user
        Optional<LocationEntity> locationOpt =
                locationRepository.findLocationByGeonameIdAndUserId(geonameId, currentUser.getId());
        if (locationOpt.isPresent()) {
            return locationOpt;
        }

        // Fallback to global scope
        return locationRepository.findLocationByGeonameIdAndScope(geonameId, Scope.GLOBAL);
    }

    /**
     * Resolves a location by geonameId for a SUB_CLIENT user, cascading through the user's own
     * records, the parent user's records, and then the GLOBAL scope.
     */
    private Optional<LocationEntity> findLocationForSubClient(
            Long geonameId, UserEntity currentUser) {
        // Try current user
        Optional<LocationEntity> locationOpt =
                locationRepository.findLocationByGeonameIdAndUserId(geonameId, currentUser.getId());
        if (locationOpt.isPresent()) {
            return locationOpt;
        }

        // Try parent user
        if (currentUser.getParent() != null) {
            locationOpt =
                    locationRepository.findLocationByGeonameIdAndUserId(
                            geonameId, currentUser.getParent().getId());
            if (locationOpt.isPresent()) {
                return locationOpt;
            }
        }

        // Fallback to global scope
        return locationRepository.findLocationByGeonameIdAndScope(geonameId, Scope.GLOBAL);
    }

    @Override
    public boolean isLocationUnchanged(LocationDto locationDto, boolean isFromLocationRequest) {
        if (locationDto == null) {
            return true;
        }

        UserEntity currentUser =
                userRepository
                        .findById(UserContextHolder.get().id())
                        .orElseThrow(() -> new NotFoundException(UserServiceImpl.USER_NOT_FOUND));

        Optional<LocationEntity> locationEntityOpt;

        if (isFromLocationRequest) {
            locationEntityOpt =
                    locationRepository.findLocationByGeonameIdAndUserId(
                            locationDto.getGeonameId(), currentUser.getId());
        } else {
            locationEntityOpt = findLocationWithFallback(locationDto.getGeonameId(), currentUser);
        }

        if (locationEntityOpt.isEmpty()) {
            return false;
        }

        LocationEntity locationEntity = locationEntityOpt.get();
        LocationDto existingDto =
                toLocationDto(
                        locationEntity,
                        locationNameRepository.findAllByLocationId(locationEntity.getId()));

        // Create copies without ID for comparison
        LocationDto dtoWithoutId = copyLocationDtoWithoutId(locationDto);
        LocationDto existingDtoWithoutId = copyLocationDtoWithoutId(existingDto);

        log.debug(
                "{} object compare (excluding ID) is : {}",
                locationEntity.getLocationLevel(),
                dtoWithoutId.equals(existingDtoWithoutId));
        return dtoWithoutId.equals(existingDtoWithoutId);
    }

    /**
     * Returns a shallow copy of the given DTO with the id field cleared, used for equality
     * comparisons that should ignore database-assigned IDs.
     */
    private LocationDto copyLocationDtoWithoutId(LocationDto source) {
        LocationDto copy = new LocationDto();
        copy.setLocationCode(source.getLocationCode());
        copy.setGeonameId(source.getGeonameId());
        copy.setAttributes(source.getAttributes());
        copy.setNames(source.getNames());
        return copy;
    }

    /**
     * Returns {@code true} when the DTO carries only a non-null geonameId and every other field is
     * null or empty, indicating a lookup-by-geonameId reference rather than a full location
     * payload.
     */
    private boolean isLocationOnlyProvideGeonameId(
            LocationDto dto, boolean isFromLocationRequest, String locationLevel) {

        if (dto == null || isFromLocationRequest) {
            return false;
        }
        if (dto.getGeonameId() == null) {
            return false;
        }

        boolean onlyGeonameId =
                (dto.getLocationCode() == null || dto.getLocationCode().isBlank())
                        && (dto.getNames() == null || dto.getNames().isEmpty())
                        && (dto.getAttributes() == null || dto.getAttributes().isEmpty());

        if (onlyGeonameId) {
            log.debug("{} location DTO object and location level is : {}", dto, locationLevel);
        }
        return onlyGeonameId;
    }

    @Override
    public List<LocationEntity> findLocationByGeonameIdsWithFallback(
            List<Long> geonameIds, UserEntity currentUser) {

        return switch (currentUser.getUserType()) {
            case SUB_CLIENT -> findLocationByGeonameIdsForSubClient(geonameIds, currentUser);
            case CLIENT -> findLocationByGeonameIdsForClient(geonameIds, currentUser);
            default ->
                    locationRepository.findLocationByGeonameIdInAndUserId(
                            geonameIds, currentUser.getId());
        };
    }

    /**
     * Resolves multiple locations by geonameId for a CLIENT user, falling back to the GLOBAL scope
     * when the user has no matching records.
     */
    private List<LocationEntity> findLocationByGeonameIdsForClient(
            List<Long> geonameIds, UserEntity currentUser) {
        // Try current user
        List<LocationEntity> locationEntities =
                locationRepository.findLocationByGeonameIdInAndUserId(
                        geonameIds, currentUser.getId());
        if (!locationEntities.isEmpty()) {
            return locationEntities;
        }

        // Fallback to global scope
        return locationRepository.findLocationByGeonameIdInAndScope(geonameIds, Scope.GLOBAL);
    }

    /**
     * Resolves multiple locations by geonameId for a SUB_CLIENT user, cascading through the user,
     * the parent user, and the GLOBAL scope.
     */
    private List<LocationEntity> findLocationByGeonameIdsForSubClient(
            List<Long> geonameIds, UserEntity currentUser) {
        // Try current user
        List<LocationEntity> locationEntities =
                locationRepository.findLocationByGeonameIdInAndUserId(
                        geonameIds, currentUser.getId());
        if (!locationEntities.isEmpty()) {
            return locationEntities;
        }

        // Try parent user
        if (currentUser.getParent() != null) {
            locationEntities =
                    locationRepository.findLocationByGeonameIdInAndUserId(
                            geonameIds, currentUser.getParent().getId());
            if (!locationEntities.isEmpty()) {
                return locationEntities;
            }
        }

        // Fallback to global scope
        return locationRepository.findLocationByGeonameIdInAndScope(geonameIds, Scope.GLOBAL);
    }

    @Override
    public Map<LocationLevel, LocationDto> createLocation(
            UserEntity user,
            Map<LocationLevel, LocationDto> allLocationMap,
            boolean isFromLocationRequest) {
        List<Long> geonameIds =
                allLocationMap.values().stream()
                        .filter(Objects::nonNull) // filter null LocationDto values
                        .map(LocationDto::getGeonameId)
                        .filter(Objects::nonNull) // filter null geonameId values
                        .toList();

        List<LocationEntity> locationEntities =
                findLocationByGeonameIdsWithFallback(geonameIds, user);

        boolean shouldCreateNewLocation =
                !locationEntities.isEmpty() && locationEntities.size() != geonameIds.size();
        log.debug(
                "locationEntities : {} , shouldCreateNewLocation : {}",
                locationEntities,
                shouldCreateNewLocation);

        if (isAnyLocationOnlyProvideGeonameId(allLocationMap, isFromLocationRequest)) {

            LocationDto locationSmallestDto = getSmallestLocationDto(allLocationMap);
            if (locationSmallestDto == null) {
                throw new NotFoundException("Location request object not found");
            }

            LocationEntity locationEntity =
                    findLocationWithFallback(locationSmallestDto.getGeonameId(), user)
                            .orElseThrow(LocationNotFoundException::new);

            return findByGeonameIdAndTargetUserId(
                    locationEntity.getGeonameId(),
                    locationEntity.getUser().getId(),
                    allLocationMap);
        }

        if (!shouldCreateNewLocation
                && isAllLocationInfoUnchanged(allLocationMap, isFromLocationRequest)) {
            return buildUnchangedResponse(allLocationMap, locationEntities);
        }

        return saveAndPopulateLocationResponseDto(user, allLocationMap);
    }

    /**
     * Returns the most specific (lowest hierarchy level) non-null location from the request,
     * traversing from CITY up to CONTINENT.
     */
    private LocationDto getSmallestLocationDto(Map<LocationLevel, LocationDto> allLocationMap) {
        return allLocationMap.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .max(Comparator.comparingInt(e -> e.getKey().getOrder()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * Returns {@code true} when every location component in the request (continent, country,
     * additionalLocations, city) is unchanged relative to the stored record.
     */
    private boolean isAllLocationInfoUnchanged(
            Map<LocationLevel, LocationDto> allLocationMap, boolean isFromLocationRequest) {
        return allLocationMap.values().stream()
                .allMatch(locationDto -> isLocationUnchanged(locationDto, isFromLocationRequest));
    }

    /**
     * Returns {@code true} if any of the continent, country, or city DTOs in the request contains
     * only a geonameId with no other payload.
     */
    private boolean isAnyLocationOnlyProvideGeonameId(
            Map<LocationLevel, LocationDto> allLocationMap, boolean isFromLocationRequest) {
        return allLocationMap.entrySet().stream()
                .filter(
                        e ->
                                e.getKey().name().equals(LOCATION_LEVEL_CONTINENT)
                                        || e.getKey().name().equals(LOCATION_LEVEL_COUNTRY)
                                        || e.getKey().name().equals(LOCATION_LEVEL_CITY))
                .anyMatch(
                        e ->
                                isLocationOnlyProvideGeonameId(
                                        e.getValue(), isFromLocationRequest, e.getKey().name()));
    }

    /**
     * Assembles a {@link LocationResponseDto} from the request DTOs, enriching each with the
     * database id resolved from the existing location entities.
     */
    private Map<LocationLevel, LocationDto> buildUnchangedResponse(
            Map<LocationLevel, LocationDto> allLocationMap, List<LocationEntity> locationEntities) {
        Map<Long, Long> geonameIdToIdMap =
                locationEntities.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        LocationEntity::getGeonameId,
                                        LocationEntity::getId,
                                        (existing, replacement) -> existing));
        Map<LocationLevel, LocationDto> response = new HashMap<>();
        allLocationMap.forEach(
                (k, v) -> {
                    setLocationIdIfPresent(v, geonameIdToIdMap);
                    response.put(k, v);
                });

        return response;
    }

    /**
     * Sets the id on the given DTO by looking up its geonameId in the provided map; no-op if the
     * DTO or its geonameId is null.
     */
    private void setLocationIdIfPresent(LocationDto locationDto, Map<Long, Long> geonameIdToIdMap) {
        if (locationDto != null && locationDto.getGeonameId() != null) {
            Long id = geonameIdToIdMap.get(locationDto.getGeonameId());
            if (id != null) {
                locationDto.setId(id);
            }
        }
    }

    /**
     * Persists each level of the location hierarchy in top-down order and returns a populated
     * {@link LocationResponseDto}.
     */
    private Map<LocationLevel, LocationDto> saveAndPopulateLocationResponseDto(
            UserEntity user, Map<LocationLevel, LocationDto> allLocationMap) {
        Scope scope = UserContextHolder.get().scope();
        List<Map.Entry<LocationLevel, LocationDto>> sortedEntries =
                allLocationMap.entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> e.getKey().getOrder()))
                        .toList();

        Map<LocationLevel, LocationDto> response = new HashMap<>();
        Long parentId = null;

        for (Map.Entry<LocationLevel, LocationDto> entry : sortedEntries) {
            parentId = saveIfPresent(entry.getKey(), entry.getValue(), scope, parentId, user);
            if (entry.getValue() != null) {
                response.put(entry.getKey(), entry.getValue());
            }
        }
        return response;
    }

    /**
     * Persists the location DTO at the given level if non-null and returns its saved id, or the
     * existing parentId if the DTO is null.
     */
    private Long saveIfPresent(
            LocationLevel level, LocationDto dto, Scope scope, Long parentId, UserEntity user) {
        if (dto == null) {
            return parentId;
        }
        // CONTINENT has order 1
        if (level.getOrder() != 1 && (parentId == null || parentId <= 0)) {
            throw new GlobalException(
                    HttpStatus.BAD_REQUEST, "Parent Id is null or empty: " + parentId);
        }
        Long savedId = saveLocation(dto, level, scope, parentId, user);
        dto.setId(savedId);
        return savedId;
    }

    /**
     * Persists or updates a single location record, enforcing ownership checks, setting hierarchy
     * metadata, and saving localized name rows.
     */
    private Long saveLocation(
            LocationDto dto,
            LocationLevel level,
            Scope scope,
            Long parentId,
            UserEntity requester) {

        Optional<LocationEntity> existLocationEntityOpt =
                locationRepository.findLocationByGeonameIdAndUserId(
                        dto.getGeonameId(), requester.getId());
        existLocationEntityOpt.ifPresent(
                locationEntity ->
                        userService.checkUserAccess(
                                UserContextHolder.get(), locationEntity.getUser()));

        LocationEntity locationEntity =
                existLocationEntityOpt.orElseGet(() -> locationMapper.dtoToEntity(dto));

        if (existLocationEntityOpt.isPresent()) {
            LocationEntity existing = existLocationEntityOpt.get();
            LocationEntity incoming = locationMapper.dtoToEntity(dto);

            if (isUpdateNeeded(existing, incoming)) {
                existing.setLocationCode(incoming.getLocationCode());
                existing.setGeonameId(incoming.getGeonameId());
                existing.setAttributes(incoming.getAttributes());
                updateLocationNames(existing, dto);
            }
        }

        locationEntity.setLocationLevel(level.name());
        locationEntity.setUser(requester);
        locationEntity.setScope(scope);

        if (parentId != null) {
            LocationEntity parent =
                    locationRepository
                            .findById(parentId)
                            .orElseThrow(
                                    () ->
                                            new LocationNotFoundException(
                                                    "Parent location not found"));
            locationEntity.setParent(parent);
        }

        locationRepository.save(locationEntity);

        log.debug("{} Data Saved", level.name());

        locationEntity.setPathIds(buildPathIds(locationEntity.getParent(), locationEntity.getId()));

        List<LocationNameEntity> names = getLocationNameEntities(dto, locationEntity.getId());
        if (!names.isEmpty()) {
            locationNameRepository.saveAll(names);
        }
        return locationEntity.getId();
    }

    @CacheEvict(
            value = CACHE_NAME_LOCATION_BY_ID,
            allEntries = true,
            cacheManager = CACHE_MANAGER_CAFFEINE)
    @Transactional
    @Override
    public LocationDto update(Long id, LocationDto request) {

        Optional<LocationEntity> locationEntityOpt = locationRepository.findById(id);
        if (locationEntityOpt.isEmpty()) {
            throw new LocationNotFoundException("Location not found");
        }

        userService.checkUserAccess(UserContextHolder.get(), locationEntityOpt.get().getUser());

        LocationEntity locationEntity = locationEntityOpt.get();

        updateLocationNames(locationEntity, request);

        locationEntity.setAttributes(request.getAttributes());
        locationEntity.setGeonameId(request.getGeonameId());
        locationEntity.setLocationCode(request.getLocationCode());
        locationRepository.save(locationEntity);
        request.setId(id);
        return request;
    }

    /** Replaces all existing name rows for the entity with the names supplied in the DTO. */
    private void updateLocationNames(LocationEntity entity, LocationDto dto) {
        locationNameRepository.deleteByLocationId(entity.getId());
        List<LocationNameEntity> names = getLocationNameEntities(dto, entity.getId());
        locationNameRepository.saveAll(names);
    }

    @Override
    public boolean isUpdateNeeded(LocationEntity existing, LocationEntity incoming) {
        // Compare locationCode - only if incoming is not empty and different
        if (StringUtils.isNotBlank(incoming.getLocationCode())
                && !Objects.equals(existing.getLocationCode(), incoming.getLocationCode())) {
            return true;
        }

        // Compare geonameId - only if incoming is not null and different
        if (incoming.getGeonameId() != null
                && !Objects.equals(existing.getGeonameId(), incoming.getGeonameId())) {
            return true;
        }

        // Compare attributes - only if incoming is not empty and different
        Map<String, Object> incomingAttrs = incoming.getAttributes();
        if (incomingAttrs != null && !incomingAttrs.isEmpty()) {
            Map<String, Object> existingAttrs = existing.getAttributes();
            return !Objects.equals(existingAttrs, incomingAttrs);
        }
        return false;
    }

    @Override
    public String buildPathIds(LocationEntity parent, long id) {
        return Optional.ofNullable(parent)
                .map(LocationEntity::getPathIds)
                .map(parentPath -> parentPath + "." + id)
                .orElse(String.valueOf(id));
    }
}
