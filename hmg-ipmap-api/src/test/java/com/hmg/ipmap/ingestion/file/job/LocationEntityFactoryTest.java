package com.hmg.ipmap.ingestion.file.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.model.CityTerritory;
import com.hmg.ipmap.ingestion.file.job.model.ContinentTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CountryTerritory;
import com.hmg.ipmap.ingestion.file.job.model.RegionTerritory;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationEntityFactoryTest {

    private UserEntity user;
    private LocationEntity parent;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);

        parent = new LocationEntity();
        parent.setId(1L);
    }

    // ──────────────────────────────────────────────────────────────
    // newContinent
    // ──────────────────────────────────────────────────────────────

    @Test
    void toContinent_Entity_ShouldMapAllFields() {
        ContinentTerritory continent =
                ContinentTerritory.builder()
                        .continentCode("AS")
                        .continentName("Asia")
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(continent, user);

        assertThat(result.getId()).isNull(); // id not set until DB persist
        assertThat(result.getGeonameId()).isNull();
        assertThat(result.getLocationCode()).isEqualTo("AS");
        assertThat(result.getLocationLevel()).isEqualTo("CONTINENT");
        assertThat(result.getParent()).isNull();
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getAttributes()).containsEntry("is_in_european_union", false);
        assertThat(result.getPathIds()).isNull(); // pathIds not set until after DB persist
    }

    @Test
    void toContinent_Entity_ShouldSetEuropeanUnionFlag_WhenTrue() {
        ContinentTerritory continent =
                ContinentTerritory.builder()
                        .continentCode("EU")
                        .continentName("Europe")
                        .isInEuropeanUnion(true)
                        .build();

        LocationEntity result = LocationEntityFactory.of(continent, user);

        assertThat(result.getAttributes()).containsEntry("is_in_european_union", true);
    }

    // ──────────────────────────────────────────────────────────────
    // newCountry
    // ──────────────────────────────────────────────────────────────

    @Test
    void toCountry_Entity_ShouldMapAllFields() {
        CountryTerritory item =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(1835841L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getId()).isNull();
        assertThat(result.getGeonameId()).isEqualTo(1835841L);
        assertThat(result.getLocationCode()).isEqualTo("KR");
        assertThat(result.getLocationLevel()).isEqualTo("COUNTRY");
        assertThat(result.getParent()).isEqualTo(parent);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getPathIds()).isNull();
    }

    @Test
    void toCountry_Entity_ShouldLinkToParent_WhenParentHasPath() {
        parent.setPathIds("CNTNT_AS");
        CountryTerritory item =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(1835841L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getParent()).isSameAs(parent);
        assertThat(result.getPathIds()).isNull(); // pathIds computed after DB persist
    }

    @Test
    void toCountry_Entity_ShouldSetGeonameIdToNull_WhenGeonameIdIsNull() {
        CountryTerritory item =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(null)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getGeonameId()).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // new region
    // ──────────────────────────────────────────────────────────────

    @Test
    void toRegion_Entity_ShouldMapAllFields_ForLevel1() {
        RegionTerritory item =
                RegionTerritory.builder()
                        .regionCode("KR")
                        .regionName("Seoul")
                        .geonameId(1835848L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getId()).isNull();
        assertThat(result.getGeonameId()).isEqualTo(1835848L);
        assertThat(result.getLocationCode()).isEqualTo("KR");
        assertThat(result.getLocationLevel()).isEqualTo("REGION");
        assertThat(result.getParent()).isEqualTo(parent);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getPathIds()).isNull();
    }

    @Test
    void toRegion_Entity_ShouldLinkToParent_WhenParentHasPath() {
        parent.setPathIds("CNTNT_AS.CNTRY_KR");
        RegionTerritory item =
                RegionTerritory.builder()
                        .regionCode("KR")
                        .regionName("Seoul")
                        .geonameId(1835848L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getParent()).isSameAs(parent);
        assertThat(result.getPathIds()).isNull();
    }

    @Test
    void toRegion_Entity_ShouldSetLevel2_WhenLevelIs2() {
        RegionTerritory item =
                RegionTerritory.builder()
                        .regionCode("KR")
                        .regionName("Seoul")
                        .geonameId(1835848L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getLocationLevel()).isEqualTo("REGION");
    }

    @Test
    void toRegion_Entity_ShouldSetGeonameIdToNull_WhenGeonameIdIsNull() {
        RegionTerritory item =
                RegionTerritory.builder()
                        .regionCode("KR")
                        .regionName("Seoul")
                        .geonameId(null)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getGeonameId()).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // newCity
    // ──────────────────────────────────────────────────────────────

    @Test
    void toCity_Entity_ShouldMapAllFields() {
        CityTerritory item =
                CityTerritory.builder()
                        .cityName("Seoul")
                        .geonameId(1835847L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getId()).isNull();
        assertThat(result.getGeonameId()).isEqualTo(1835847L);
        assertThat(result.getLocationCode()).isNull();
        assertThat(result.getLocationLevel()).isEqualTo("CITY");
        assertThat(result.getParent()).isEqualTo(parent);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getAttributes()).containsEntry("is_in_european_union", false);
        assertThat(result.getPathIds()).isNull();
    }

    @Test
    void toCity_Entity_ShouldLinkToParent_WhenParentHasPath() {
        parent.setPathIds("CNTNT_AS.CNTRY_KR.SBDVSN_11");
        CityTerritory item =
                CityTerritory.builder()
                        .cityName("Seoul")
                        .geonameId(1835847L)
                        .isInEuropeanUnion(false)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getParent()).isSameAs(parent);
        assertThat(result.getPathIds()).isNull();
    }

    @Test
    void toCity_Entity_ShouldSetEuropeanUnionFlag_WhenTrue() {
        CityTerritory item =
                CityTerritory.builder()
                        .cityName("Paris")
                        .geonameId(2988507L)
                        .isInEuropeanUnion(true)
                        .build();

        LocationEntity result = LocationEntityFactory.of(item, parent, user);

        assertThat(result.getAttributes()).containsEntry("is_in_european_union", true);
    }
}
