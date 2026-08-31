package com.hmg.ipmap.ingestion.file.job.model;

import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Getter
@SuperBuilder
public abstract class BaseLocation {
    protected Long fileDetailId;
    protected String localeCode;
    protected final ContinentTerritory continent;
    protected CountryTerritory country;

    protected abstract String determineAndGetLevel();

    public abstract Long extractGeonameId();

    public abstract List<String> extractIsoCodes();

    public abstract int order();

    protected static List<String> putContinentAndCountry(
            ContinentTerritory continent, CountryTerritory country, List<String> result) {
        Optional.ofNullable(country)
                .filter(c -> StringUtils.isNotEmpty(c.getCountryIsoCode()))
                .map(CountryTerritory::getCountryIsoCode)
                .ifPresent(result::add);

        Optional.ofNullable(continent)
                .filter(c -> StringUtils.isNotEmpty(c.getContinentCode()))
                .map(ContinentTerritory::getContinentCode)
                .ifPresent(result::add);

        return result;
    }
}
