package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.model.AbstractTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CityTerritory;
import com.hmg.ipmap.ingestion.file.job.model.ContinentTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CountryTerritory;
import com.hmg.ipmap.ingestion.file.job.model.RegionTerritory;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationServiceImpl;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.user.UserEntity;
import java.util.Map;

public class LocationEntityFactory {
    protected LocationEntityFactory() {
        /* This utility class should not be instantiated */
    }

    /**
     * Creates an unpersisted {@link LocationEntity} for a continent.
     *
     * @param continent the source continent item
     * @param user the user who owns the entity
     * @return a new, unpersisted continent entity
     */
    public static LocationEntity of(ContinentTerritory continent, UserEntity user) {
        return buildLocation(
                null,
                continent.getContinentCode(),
                DefaultLocationLevel.CONTINENT.name(),
                null,
                continent,
                user);
    }

    /**
     * Creates an unpersisted {@link LocationEntity} for a country under the given continent.
     *
     * @param country the source country item
     * @param parent the parent continent entity
     * @param user the user who owns the entity
     * @return a new, unpersisted country entity
     */
    public static LocationEntity of(
            CountryTerritory country, LocationEntity parent, UserEntity user) {
        return buildLocation(
                country.getGeonameId(),
                country.getCountryIsoCode(),
                DefaultLocationLevel.COUNTRY.name(),
                parent,
                country,
                user);
    }

    /**
     * Creates an unpersisted {@link LocationEntity} for a region (subdivision level 1) under the
     * given country.
     *
     * @param region the source region item
     * @param parent the parent country entity
     * @param user the user who owns the entity
     * @return a new, unpersisted region entity
     */
    public static LocationEntity of(
            RegionTerritory region, LocationEntity parent, UserEntity user) {
        return buildLocation(
                region.getGeonameId(), region.getRegionCode(), "REGION", parent, region, user);
    }

    /**
     * Creates an unpersisted {@link LocationEntity} for a city under the given parent.
     *
     * @param city the source city item
     * @param parent the deepest available parent (subdivision2, subdivision1, or country)
     * @param user the user who owns the entity
     * @return a new, unpersisted city entity
     */
    public static LocationEntity of(CityTerritory city, LocationEntity parent, UserEntity user) {
        return buildLocation(
                city.getGeonameId(), null, DefaultLocationLevel.CITY.name(), parent, city, user);
    }

    /**
     * Builds a new, unpersisted {@link LocationEntity} from the given parameters.
     *
     * <p>{@code pathIds} is intentionally NOT set here because the entity has no database-generated
     * {@code id} yet. The caller must persist the entity first (to obtain the id) and then call
     * {@link LocationServiceImpl#buildPathIds} to compute and store the path.
     */
    protected static LocationEntity buildLocation(
            Long geonameId,
            String code,
            String level,
            LocationEntity parent,
            AbstractTerritory source,
            UserEntity user) {
        LocationEntity entity = new LocationEntity();
        entity.setGeonameId(geonameId);
        entity.setLocationCode(code);
        entity.setLocationLevel(level);
        entity.setParent(parent);
        entity.setScope(Scope.GLOBAL);
        entity.setUser(user);
        entity.setAttributes(Map.of("is_in_european_union", source.isInEuropeanUnion()));
        return entity;
    }
}
