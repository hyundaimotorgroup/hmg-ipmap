package com.hmg.ipmap.ingestion.file.job.model;

import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.location.enums.LocationLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@SuperBuilder
public class DefaultLocation extends BaseLocation {
    private RegionTerritory region;
    private CityTerritory city;
    private LocationLevel level;

    @Override
    public String determineAndGetLevel() {
        /* Make sure to check from lowest level */
        if (continent == null) {
            return null;
        }
        if (city != null && StringUtils.isNotEmpty(city.getCityName())) {
            level = DefaultLocationLevel.CITY;
        } else if (region != null && StringUtils.isNotEmpty(region.getRegionName())) {
            level = DefaultLocationLevel.REGION;
        } else if (country != null && StringUtils.isNotEmpty(country.getCountryIsoCode())) {
            level = DefaultLocationLevel.COUNTRY;
        } else {
            return null;
        }
        return level.name();
    }

    @Override
    public Long extractGeonameId() {
        return Optional.ofNullable(city)
                .filter(cityLocation -> cityLocation.getGeonameId() != null)
                .map(CityTerritory::getGeonameId)
                .orElse(null);
    }

    @Override
    public List<String> extractIsoCodes() {
        List<String> result = new ArrayList<>();

        Optional.ofNullable(region)
                .filter(s -> s.getRegionCode() != null)
                .map(RegionTerritory::getRegionCode)
                .ifPresent(result::add);

        return putContinentAndCountry(continent, country, result);
    }

    @Override
    public int order() {
        if (level == null) {
            determineAndGetLevel();
        }
        return level.getOrder();
    }
}
