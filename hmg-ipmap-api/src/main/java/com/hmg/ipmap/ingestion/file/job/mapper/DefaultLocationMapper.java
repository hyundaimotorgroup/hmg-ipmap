package com.hmg.ipmap.ingestion.file.job.mapper;

import com.hmg.ipmap.ingestion.file.job.dto.DefaultLocationDto;
import com.hmg.ipmap.ingestion.file.job.model.CityTerritory;
import com.hmg.ipmap.ingestion.file.job.model.ContinentTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CountryTerritory;
import com.hmg.ipmap.ingestion.file.job.model.DefaultLocation;
import com.hmg.ipmap.ingestion.file.job.model.RegionTerritory;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {StringUtils.class})
public interface DefaultLocationMapper {

    default DefaultLocation toLocation(DefaultLocationDto dto) {
        if (dto == null) {
            return null;
        }

        ContinentTerritory continent = toContinent(dto);
        CountryTerritory country = toCountry(dto);

        RegionTerritory region = null;
        if (StringUtils.isNotBlank(dto.subdivisionName())) {
            region = toRegion(dto);
        }

        CityTerritory city = null;
        if (StringUtils.isNotBlank(dto.cityName())) {
            city = toCity(dto);
        }

        return DefaultLocation.builder()
                .fileDetailId(dto.fileDetailId())
                .continent(continent)
                .country(country)
                .region(region)
                .city(city)
                .build();
    }

    @Mapping(target = "continentCode", source = "continentCode")
    @Mapping(target = "continentName", source = "continentName")
    ContinentTerritory toContinent(DefaultLocationDto dto);

    @Mapping(target = "countryIsoCode", source = "countryCode")
    @Mapping(target = "countryName", source = "countryName")
    @Mapping(target = "geonameId", source = "countryGeonameId")
    CountryTerritory toCountry(DefaultLocationDto dto);

    @Mapping(target = "regionCode", source = "subdivisionCode")
    @Mapping(target = "regionName", source = "subdivisionName")
    @Mapping(target = "geonameId", source = "subdivisionGeonameId")
    RegionTerritory toRegion(DefaultLocationDto dto);

    @Mapping(target = "cityName", source = "cityName")
    @Mapping(target = "geonameId", source = "cityGeonameId")
    CityTerritory toCity(DefaultLocationDto dto);
}
