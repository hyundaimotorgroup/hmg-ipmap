package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.error.ErrorCollector;
import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;
import com.hmg.ipmap.ingestion.file.job.model.CityTerritory;
import com.hmg.ipmap.ingestion.file.job.model.ContinentTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CountryTerritory;
import com.hmg.ipmap.ingestion.file.job.model.DefaultLocation;
import com.hmg.ipmap.ingestion.file.job.model.LocationName;
import com.hmg.ipmap.ingestion.file.job.model.RegionTerritory;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationIdentity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameId;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationService;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.user.UserEntity;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.data-provider", havingValue = "default")
public class DefaultLocationIngestionServiceImpl
        implements LocationIngestionService<DefaultLocation> {

    private final LocationRepository locationRepository;
    private final LocationNameRepository locationNameRepository;
    private final BatchFileDetailRepository batchFileDetailRepository;
    private final JobParameter jobParameter;
    private final LocationService locationService;
    private final IngestionCacheService ingestionCacheService;

    @Override
    public void registerLocation(List<DefaultLocation> locations) {
        log.debug("Register {} locations", locations.size());
        UserEntity user = jobParameter.getExecutor();

        LocationProcessingContext<DefaultLocation> context =
                new LocationProcessingContext<>(locations, user);
        ingestionCacheService.preloadCache(context);

        locations.forEach(location -> registerLocation(location, user, context));
        batchUpdateToSuccess(locations);
    }

    private void registerLocation(
            DefaultLocation location,
            UserEntity user,
            LocationProcessingContext<DefaultLocation> context) {
        String level = location.determineAndGetLevel();
        if (level == null) {
            ErrorCollector.add(location.getFileDetailId(), "The data is unknown");
            return;
        }

        switch (DefaultLocationLevel.valueOf(level)) {
            case COUNTRY -> {
                LocationEntity continent =
                        findOrSaveContinent(location.getContinent(), user, context);
                findOrSaveCountry(location.getCountry(), continent, user, context);
            }
            case REGION -> {
                LocationEntity continent =
                        findOrSaveContinent(location.getContinent(), user, context);
                LocationEntity country =
                        findOrSaveCountry(location.getCountry(), continent, user, context);
                findOrSaveRegion(location.getRegion(), country, user, context);
            }
            case CITY -> {
                LocationEntity continent =
                        findOrSaveContinent(location.getContinent(), user, context);
                LocationEntity country =
                        findOrSaveCountry(location.getCountry(), continent, user, context);
                LocationEntity parent =
                        location.getRegion() != null
                                ? findOrSaveRegion(location.getRegion(), country, user, context)
                                : country;
                findOrSaveCity(location.getCity(), parent, user, context);
            }
            default -> ErrorCollector.add(location.getFileDetailId(), "The data is unknown");
        }
    }

    private LocationEntity findOrSaveContinent(
            ContinentTerritory continent,
            UserEntity user,
            LocationProcessingContext<DefaultLocation> context) {
        String key =
                LocationIdentity.of(DefaultLocationLevel.CONTINENT, continent.getContinentCode());
        LocationEntity cached = context.getLocationCache().get(key);
        if (cached != null) {
            saveLocationName(cached.getId(), continent.getContinentName(), context);
            return cached;
        }
        LocationEntity entity =
                locationRepository
                        .findByLocationCodeAndLocationLevel(
                                continent.getContinentCode(), DefaultLocationLevel.CONTINENT.name())
                        .orElseGet(() -> saveNew(LocationEntityFactory.of(continent, user)));
        context.getLocationCache().put(key, entity);
        saveLocationName(entity.getId(), continent.getContinentName(), context);
        return entity;
    }

    private LocationEntity findOrSaveCountry(
            CountryTerritory country,
            LocationEntity parent,
            UserEntity user,
            LocationProcessingContext<DefaultLocation> context) {
        String key = LocationIdentity.of(DefaultLocationLevel.COUNTRY, country.getCountryIsoCode());
        LocationEntity cached = context.getLocationCache().get(key);
        if (cached != null) {
            saveLocationName(cached.getId(), country.getCountryName(), context);
            return cached;
        }
        LocationEntity entity =
                locationRepository
                        .findByLocationCodeAndLocationLevel(
                                country.getCountryIsoCode(), DefaultLocationLevel.COUNTRY.name())
                        .orElseGet(() -> saveNew(LocationEntityFactory.of(country, parent, user)));
        context.getLocationCache().put(key, entity);
        saveLocationName(entity.getId(), country.getCountryName(), context);
        return entity;
    }

    private LocationEntity findOrSaveRegion(
            RegionTerritory region,
            LocationEntity parent,
            UserEntity user,
            LocationProcessingContext<DefaultLocation> context) {
        String key = LocationIdentity.of(DefaultLocationLevel.REGION, region.getRegionCode());
        LocationEntity cached = context.getLocationCache().get(key);
        if (cached != null) {
            saveLocationName(cached.getId(), region.getRegionName(), context);
            return cached;
        }
        LocationEntity entity =
                locationRepository
                        .findByLocationCodeAndLocationLevel(
                                region.getRegionCode(), DefaultLocationLevel.REGION.name())
                        .orElseGet(() -> saveNew(LocationEntityFactory.of(region, parent, user)));
        context.getLocationCache().put(key, entity);
        saveLocationName(entity.getId(), region.getRegionName(), context);
        return entity;
    }

    private void findOrSaveCity(
            CityTerritory city,
            LocationEntity parent,
            UserEntity user,
            LocationProcessingContext<DefaultLocation> context) {
        if (city.getGeonameId() == null) {
            saveNew(LocationEntityFactory.of(city, parent, user));
            return;
        }
        String key = LocationIdentity.of(DefaultLocationLevel.CITY, city.getGeonameId().toString());
        LocationEntity cached = context.getLocationCache().get(key);
        if (cached != null) {
            saveLocationName(cached.getId(), city.getCityName(), context);
            return;
        }
        LocationEntity entity =
                locationRepository
                        .findLocationByGeonameIdAndScope(city.getGeonameId(), Scope.GLOBAL)
                        .orElseGet(() -> saveNew(LocationEntityFactory.of(city, parent, user)));
        context.getLocationCache().put(key, entity);
        saveLocationName(entity.getId(), city.getCityName(), context);
    }

    /**
     * Saves a location name immediately, skipping the write if the same name was already persisted
     * for this location during the current batch (tracked via {@code locationNameCache}).
     */
    private void saveLocationName(
            Long locationId, String name, LocationProcessingContext<DefaultLocation> context) {
        LocationNameId key = new LocationNameId(locationId, context.getLocaleCode());
        LocationNameEntity existing = context.getLocationNameCache().get(key);
        if (existing != null && Objects.equals(existing.getName(), name)) {
            return;
        }
        LocationNameEntity saved =
                locationNameRepository.save(
                        new LocationNameEntity(locationId, context.getLocaleCode(), name));
        context.getLocationNameCache().put(key, saved);
    }

    private LocationEntity saveNew(LocationEntity entity) {
        LocationEntity saved = locationRepository.save(entity);
        saved.setPathIds(locationService.buildPathIds(saved.getParent(), saved.getId()));
        return locationRepository.save(saved);
    }

    /** Marks all error-free file detail records as SUCCESS in a single batch call. */
    private void batchUpdateToSuccess(List<DefaultLocation> locations) {
        List<Long> successIds =
                locations.stream()
                        .map(BaseLocation::getFileDetailId)
                        .filter(ErrorCollector::hasNoError)
                        .toList();
        if (!successIds.isEmpty()) {
            batchFileDetailRepository.updateAllToSuccessInBatch(successIds);
        }
    }

    @Override
    public void registerLocationName(Set<LocationName> items) {
        // Not applicable for this provider: location names are embedded in the location CSV itself
    }
}
