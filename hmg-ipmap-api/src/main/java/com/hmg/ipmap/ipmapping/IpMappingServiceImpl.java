package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.dto.IpMappingLocationDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationService;
import com.hmg.ipmap.location.dto.BaseLocationRequestDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import com.hmg.ipmap.location.enums.LocationLevel;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for IP mapping CRUD operations and IP span management.
 *
 * <p>Orchestrates entity construction (via {@link IpMappingFactory}), location resolution,
 * attribute and span lifecycle, and response assembly. Access control is enforced per operation
 * using the authenticated user context from {@link UserContextHolder}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IpMappingServiceImpl implements IpMappingService {

    private final IpMappingRepository ipMappingRepository;
    private final IpMappingMapper ipMappingMapper;
    private final LocationService locationService;
    private final UserService userService;
    private final IpMappingAttributeMapper ipMappingAttributeMapper;
    private final IpMappingResponseBuilder ipMappingResponseBuilder;
    private final IpSpanService ipSpanService;
    private final IpSpanProperties ipSpanProperties;
    private final IpMappingFactory ipMappingFactory;
    private final IpMappingAttributeService ipMappingAttributeService;
    private final LocationRepository locationRepository;
    private final LocationNameRepository locationNameRepository;

    public static final String IP_MAPPING_NOT_FOUND = "Ip Mapping Not Found";

    @Transactional(readOnly = true)
    @Override
    public PaginationResponse<IpMappingResponseDto> searchWithPagination(PaginationRequest req) {

        int page = (req == null) ? 0 : Math.max(0, req.pageOrDefault());
        int size = (req == null) ? 10 : Math.max(1, req.sizeOrDefault());

        Pageable pageable = PageRequest.of(page, size);
        log.trace("pageable={}", pageable);
        Page<IpMappingEntity> ipMappingEntityPage =
                switch (UserContextHolder.get().userType()) {
                    case SUB_CLIENT ->
                            ipMappingRepository.findAllByScope(Scope.SUB_CLIENT, pageable);
                    case CLIENT -> ipMappingRepository.findAllByScope(Scope.CLIENT, pageable);
                    default -> ipMappingRepository.findAllByScope(Scope.GLOBAL, pageable);
                };

        List<IpMappingResponseDto> responseList = new ArrayList<>();
        if (!ipMappingEntityPage.getContent().isEmpty()) {
            responseList.addAll(buildResponseDtos(ipMappingEntityPage.getContent()));
        }

        return new PaginationResponse<>(
                responseList,
                ipMappingEntityPage.isLast(),
                ipMappingEntityPage.getTotalElements(),
                ipMappingEntityPage.getTotalPages(),
                ipMappingEntityPage.isFirst(),
                ipMappingEntityPage.getSize(),
                ipMappingEntityPage.getNumber(),
                ipMappingEntityPage.getNumberOfElements(),
                ipMappingEntityPage.isEmpty());
    }

    @Transactional(readOnly = true)
    @Override
    public IpMappingResponseDto searchById(Long id) {

        IpMappingEntity ipMappingEntity =
                ipMappingRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException(IP_MAPPING_NOT_FOUND));

        userService.checkUserAccess(UserContextHolder.get(), ipMappingEntity.getUser());

        return buildSingleResponseDto(ipMappingEntity);
    }

    @Transactional
    @Override
    public IpMappingResponseDto create(IpMappingRequestDto ipMappingRequestDto) {
        log.trace("Validating ip mapping.");

        UserEntity currentUser = userService.getEntityById(UserContextHolder.get().id());

        Map<LocationLevel, LocationDto> locationMap =
                createLocation(currentUser, ipMappingRequestDto.location());

        BaseLocationRequestDto loc = ipMappingRequestDto.location();
        locationMap.forEach(
                (level, dto) -> {
                    switch (level.name()) {
                        case "CONTINENT" -> loc.setContinent(dto);
                        case "COUNTRY" -> loc.setCountry(dto);
                        case "CITY" -> loc.setCity(dto);
                        default -> {
                            /* Skip */
                        }
                    }
                });

        Long smallestGeonameId = getSmallestGeonameId(ipMappingRequestDto.location());

        log.trace("prepare to insert new ip mapping.");
        ipMappingFactory.validateIpNotation(ipMappingRequestDto.ipNotation());

        // validation representedCountry and registeredCountry
        Optional.ofNullable(ipMappingRequestDto.representedCountryGeonameId())
                .ifPresent(
                        id ->
                                locationService
                                        .findLocationWithFallback(id, currentUser)
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "representedCountry geoname id not found")));
        Optional.ofNullable(ipMappingRequestDto.registeredCountryGeonameId())
                .ifPresent(
                        id ->
                                locationService
                                        .findLocationWithFallback(id, currentUser)
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "registeredCountry geoname id not found")));

        IpMappingEntity ipMappingEntity =
                ipMappingFactory.buildForCreate(
                        ipMappingRequestDto,
                        currentUser,
                        UserContextHolder.get().scope(),
                        smallestGeonameId);

        log.trace("Creating new ip mapping");
        IpMappingEntity savedMapping = ipMappingRepository.save(ipMappingEntity);

        updateIpMappingAttributeAndIpSpan(ipMappingRequestDto, savedMapping);

        log.trace("Set ip mapping response");
        return buildResponse(ipMappingRequestDto, savedMapping);
    }

    @Transactional
    @Override
    public IpMappingResponseDto update(Long id, IpMappingRequestDto ipMappingRequestDto) {
        log.trace("Validating user and data");
        UserEntity currentUser = userService.getEntityById(UserContextHolder.get().id());

        ipMappingFactory.validateIpNotation(ipMappingRequestDto.ipNotation());

        IpMappingEntity existingIpMappingEntity =
                ipMappingRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException(IP_MAPPING_NOT_FOUND));

        userService.checkUserAccess(UserContextHolder.get(), existingIpMappingEntity.getUser());

        // validation representedCountry and registeredCountry
        Optional.ofNullable(ipMappingRequestDto.representedCountryGeonameId())
                .ifPresent(
                        geonameId ->
                                locationService
                                        .findLocationWithFallback(geonameId, currentUser)
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "representedCountry geoname id not found")));
        Optional.ofNullable(ipMappingRequestDto.registeredCountryGeonameId())
                .ifPresent(
                        geonameId ->
                                locationService
                                        .findLocationWithFallback(geonameId, currentUser)
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "registeredCountry geoname id not found")));

        createLocation(currentUser, ipMappingRequestDto.location());
        Long smallestGeonameId = getSmallestGeonameId(ipMappingRequestDto.location());

        log.trace("prepare to update/insert new ip mapping.");

        updateIpMappingAttributeAndIpSpan(ipMappingRequestDto, existingIpMappingEntity);

        ipMappingFactory.applyUpdates(
                existingIpMappingEntity, ipMappingRequestDto, smallestGeonameId, currentUser);

        log.trace("update ip mapping");
        IpMappingEntity savedIpMappingEntity = ipMappingRepository.save(existingIpMappingEntity);

        return buildResponse(ipMappingRequestDto, savedIpMappingEntity);
    }

    /**
     * Replaces the attribute entities and IP spans for the given mapping with those derived from
     * {@code request}.
     *
     * @param request the request containing updated location attributes
     * @param ipMappingEntity the existing IP mapping entity to update
     */
    private void updateIpMappingAttributeAndIpSpan(
            IpMappingRequestDto request, IpMappingEntity ipMappingEntity) {
        log.trace("Replacing ip mapping attributes and ip spans for {}", ipMappingEntity);
        ipMappingAttributeService.replaceAttributes(request, ipMappingEntity);
        ipSpanService.updateIpSpans(ipMappingEntity);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        log.trace("Deleting ip mapping with id: {}", id);

        IpMappingEntity existingIpMapping =
                ipMappingRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException(IP_MAPPING_NOT_FOUND));

        log.trace("Validating user access for ip mapping deletion");
        userService.checkUserAccess(UserContextHolder.get(), existingIpMapping.getUser());

        log.trace("Deleting ip spans for ip mapping");
        ipSpanService.deleteAllByIpMapping(existingIpMapping);

        log.trace("Deleting ip mapping attributes");
        ipMappingAttributeService.deleteAllByIpMapping(existingIpMapping);

        log.trace("Deleting ip mapping entity");
        ipMappingRepository.delete(existingIpMapping);

        log.trace("Successfully deleted ip mapping with id: {}", id);
    }

    @Override
    public List<IpSpanEntity> buildIpSpan(IpMappingEntity ipMappingEntity) {
        return ipSpanService.parseNotationToIpSpanList(ipMappingEntity);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<IpMappingEntity> findByIdWithAttributes(Long id) {
        return ipMappingRepository.findByIdWithAttributes(id);
    }

    @Transactional(readOnly = true)
    @Override
    public IpMappingResponseDto getAndSetIpMappingResponseDto(IpMappingEntity ipMappingEntity) {
        log.trace(
                "IpMappingResponseDto.getAndSetIpMappingResponseDto ipMappingEntity : {}",
                ipMappingEntity);

        LocationResponseDto locationResponseDto =
                locationService.findLocationHierarchy(ipMappingEntity.getLocation().getId());

        return buildIpMappingResponse(
                ipMappingEntity, locationResponseDto, ipMappingEntity.getAttributes());
    }

    @Async
    @Override
    public void rebuildAllIpSpans(long startId, long endId) {
        log.info(
                "Starting IP span rebuild for id range [{}, {}] with max prefix length /{}",
                startId,
                endId,
                ipSpanProperties.getSubnetPrefixLength());
        long currentId = startId;
        long totalProcessed = 0;

        while (currentId <= endId) {
            List<IpMappingEntity> chunk =
                    ipMappingRepository.findWithUserInRange(
                            currentId,
                            endId,
                            PageRequest.of(0, ipSpanProperties.getRebuild().getChunkSize()));

            if (chunk.isEmpty()) {
                break;
            }

            ipSpanService.rebuildIpSpans(chunk);
            totalProcessed += chunk.size();
            currentId = chunk.getLast().getId() + 1;
            log.info(
                    "IP span rebuild progress: {} ip mappings processed, next cursor id={}",
                    totalProcessed,
                    currentId);
        }

        log.info(
                "IP span rebuild completed for range [{}, {}]. Total processed: {}",
                startId,
                endId,
                totalProcessed);
    }

    @Override
    public IpMappingResponseDto getAndSetIpMappingResponseDto(
            IpMappingCacheDto ipMappingCache, IpMappingEntity ipMappingEntity) {
        log.trace(
                "IpMappingDtoResponse.getAndSetIpMappingDtoResponse ipMappingCache : {}",
                ipMappingCache);
        LocationResponseDto locationResponseDto =
                locationService.buildLocationDtoFromCache(ipMappingCache.getLocationDto(), null);
        List<IpMappingAttributeEntity> ipMappingAttributeEntityList =
                ipMappingCache.getAttributeDtos().stream()
                        .map(ipMappingAttributeMapper::cacheToEntity)
                        .toList();

        return buildIpMappingResponse(
                ipMappingEntity, locationResponseDto, ipMappingAttributeEntityList);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private IpMappingResponseDto buildResponse(IpMappingRequestDto request, IpMappingEntity saved) {
        IpMappingResponseDto response = ipMappingMapper.reqDtoToRespDto(request);
        response.setId(saved.getId());
        response.setValidPeriod(request.validPeriod());
        BaseLocationRequestDto loc = request.location();
        if (loc != null) {
            List<LocationDto> additionalLocations = loc.getAdditionalLocations();
            response.setLocation(
                    new IpMappingLocationDto(
                            loc.getContinent(),
                            loc.getCountry(),
                            additionalLocations.isEmpty() ? null : additionalLocations,
                            loc.getCity()));
        }
        return response;
    }

    private Map<LocationLevel, LocationDto> createLocation(
            UserEntity currentUser, BaseLocationRequestDto loc) {
        return locationService.createLocation(currentUser, loc.getAllLocationMap(), false);
    }

    /**
     * Fetches all related data for the given entities and delegates to {@link
     * IpMappingResponseBuilder#buildIpMappingResponseDtoList} to produce response DTOs.
     *
     * <p>Issues one query per related table (attributes, locations, location names) to avoid N+1
     * queries, then passes the pre-fetched results to the pure assembler.
     */
    private List<IpMappingResponseDto> buildResponseDtos(List<IpMappingEntity> entities) {
        List<Long> ids = entities.stream().map(IpMappingEntity::getId).toList();
        List<IpMappingAttributeEntity> attrs = ipMappingAttributeService.fetchByIpMappingIds(ids);

        List<Long> locationIds =
                entities.stream()
                        .map(IpMappingEntity::getLocation)
                        .filter(Objects::nonNull)
                        .map(LocationEntity::getId)
                        .distinct()
                        .toList();
        List<LocationEntity> locations = locationRepository.findLocationRecursiveBatch(locationIds);
        List<Long> allLocationIds = locations.stream().map(LocationEntity::getId).toList();
        List<LocationNameEntity> names =
                locationNameRepository.findAllByLocationIdIn(allLocationIds);

        return ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                entities, attrs, locations, names);
    }

    /**
     * Builds a single response DTO by delegating to {@link #buildResponseDtos}, returning an empty
     * DTO if the result list is unexpectedly empty.
     */
    private IpMappingResponseDto buildSingleResponseDto(IpMappingEntity entity) {
        List<IpMappingResponseDto> result = buildResponseDtos(List.of(entity));
        return result.isEmpty() ? new IpMappingResponseDto() : result.getFirst();
    }

    /**
     * Returns the geonameId of the most specific (leaf-level) location present in the DTO,
     * traversing from city down to continent.
     */
    private Long getSmallestGeonameId(BaseLocationRequestDto loc) {
        if (loc.getCity() != null) {
            return loc.getCity().getGeonameId();
        }
        List<LocationDto> additionalLocations = loc.getAdditionalLocations();
        if (!additionalLocations.isEmpty()) {
            return additionalLocations.getLast().getGeonameId();
        }
        if (loc.getCountry() != null) {
            return loc.getCountry().getGeonameId();
        }
        if (loc.getContinent() != null) {
            return loc.getContinent().getGeonameId();
        }
        return null;
    }

    /**
     * Resolves a {@link LocationDto} from the given geonameId using user-scoped fallback. Returns
     * {@code null} if no matching location is found.
     *
     * @param geonameId the GeoNames ID to resolve; may be {@code null}
     * @param user the user context for fallback resolution
     * @return resolved LocationDto, or {@code null} if not found or geonameId is null
     */
    private LocationDto findLocationWithFallbacktoLocationDto(Long geonameId, UserEntity user) {
        if (geonameId == null) return null;
        return locationService
                .findLocationWithFallback(geonameId, user)
                .map(e -> locationService.toLocationDto(e, e.getNames()))
                .orElse(null);
    }

    /**
     * Core builder: assembles {@link IpMappingResponseDto} from pre-resolved location and attribute
     * data, resolving registered/represented country internally.
     *
     * @param ipMappingEntity the IP mapping entity
     * @param locationResponse pre-resolved location hierarchy
     * @param attributes list of attribute entities (from DB or cache)
     * @return fully assembled {@link IpMappingResponseDto}
     */
    private IpMappingResponseDto buildIpMappingResponse(
            IpMappingEntity ipMappingEntity,
            LocationResponseDto locationResponse,
            List<IpMappingAttributeEntity> attributes) {

        LocationDto registeredCountry =
                findLocationWithFallbacktoLocationDto(
                        ipMappingEntity.getRegisteredCountryGeonameId(), ipMappingEntity.getUser());

        LocationDto representedCountry =
                findLocationWithFallbacktoLocationDto(
                        ipMappingEntity.getRepresentedCountryGeonameId(),
                        ipMappingEntity.getUser());

        return ipMappingResponseBuilder.buildIpMappingResponseDto(
                ipMappingEntity,
                locationResponse,
                attributes,
                registeredCountry,
                representedCountry);
    }
}
