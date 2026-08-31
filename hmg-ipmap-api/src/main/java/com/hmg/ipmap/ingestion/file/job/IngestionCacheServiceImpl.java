package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationIdentity;
import com.hmg.ipmap.location.LocationNameId;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service responsible for cache management operations */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionCacheServiceImpl implements IngestionCacheService {
    private final LocationRepository locationRepository;
    private final LocationNameRepository locationNameRepository;
    private final IpMappingRepository ipMappingRepository;
    private final IpBlockIngestionStepCache ipBlockStepCache;

    @Override
    public void preloadCache(LocationProcessingContext<? extends BaseLocation> context) {
        log.debug(
                "Preloading cache for job location {} location items",
                context.getLocations().size());

        loadLocationCache(context);
        loadLocationNameCache(context);

        log.debug(
                "Cache preloaded - Locations: {}, Names: {}",
                context.getLocationCache().size(),
                context.getLocationNameCache().size());
    }

    @Override
    public void preloadCache(IpBlockProcessingContext context) {
        log.debug("Preloading cache for job ip blocks {}", context.getIpBlocks().size());

        // Preloading location information — query DB only for geoname IDs not yet in the
        // step-level cache. Locations are stable during IP block processing (the location
        // phase has already completed), so they are safe to reuse across chunks.
        Set<Long> geonamesId =
                context.getIpBlocks().stream()
                        .map(IpBlock::getGeonameId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Set<Long> missing = ipBlockStepCache.getMissingGeonameIds(geonamesId);
        if (!missing.isEmpty()) {
            List<LocationEntity> loaded =
                    locationRepository.findByScopeEqualsAndGeonameIdIn(Scope.GLOBAL, missing);
            loaded.forEach(
                    location -> {
                        if (location.getGeonameId() != null) {
                            ipBlockStepCache.putLocation(location.getGeonameId(), location);
                        }
                    });
        }

        int locationCount = 0;
        for (Long id : geonamesId) {
            LocationEntity location = ipBlockStepCache.getLocation(id);
            if (location != null) {
                context.putLocation(id, location);
                locationCount++;
            }
        }
        log.debug(
                "Cache preloaded - Locations: {} ({} fetched from DB)",
                locationCount,
                missing.size());

        // Preloading ip mapping information
        Set<String> ipNotations =
                context.getIpBlocks().stream().map(IpBlock::getNetwork).collect(Collectors.toSet());
        List<IpMappingEntity> ipMappings =
                ipMappingRepository.findAllByIpNotationInWithLocation(ipNotations);
        ipMappings.forEach(
                ipMappingEntity ->
                        context.putIpMapping(ipMappingEntity.getIpNotation(), ipMappingEntity));
        log.debug("Cache preloaded - Ip mappings: {}", ipMappings.size());
    }

    private void loadLocationCache(LocationProcessingContext<? extends BaseLocation> context) {
        List<LocationEntity> locations =
                locationRepository.findByScopeEqualsAndLocationCodeIn(
                        Scope.GLOBAL, context.getIsoCodesFromLocation());

        Set<Long> geonameIds = context.getGeonameIdsFromLocation();
        List<LocationEntity> byScopeEqualsAndGeonameIdIn =
                locationRepository.findByScopeEqualsAndGeonameIdIn(Scope.GLOBAL, geonameIds);

        locations.addAll(byScopeEqualsAndGeonameIdIn);

        locations.forEach(
                location ->
                        context.getLocationCache().put(LocationIdentity.of(location), location));
    }

    private void loadLocationNameCache(LocationProcessingContext<? extends BaseLocation> context) {
        List<LocationNameId> locationNameIds =
                context.getLocationCache().values().stream()
                        .map(x -> new LocationNameId(x.getId(), context.getLocaleCode()))
                        .toList();

        locationNameRepository
                .findByLocationNameIdIn(locationNameIds)
                .forEach(
                        name -> context.getLocationNameCache().put(name.getLocationNameId(), name));
    }
}
