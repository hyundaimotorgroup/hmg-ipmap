package com.hmg.ipmap.ipmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ipmapping.dto.IpMappingLocationDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameId;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link IpMappingResponseBuilder}.
 *
 * <p>The builder has no repository dependencies — all data is passed as parameters. These tests
 * therefore need no database mocks and focus purely on DTO assembly logic.
 */
@ExtendWith(MockitoExtension.class)
class IpMappingResponseBuilderTest {

    @Mock private IpMappingMapper ipMappingMapper;

    @InjectMocks private IpMappingResponseBuilder ipMappingResponseBuilder;

    private IpMappingEntity ipMappingEntity;
    private LocationEntity continentEntity;
    private LocationEntity countryEntity;
    private LocationEntity subdivision1Entity;
    private LocationEntity subdivision2Entity;
    private LocationEntity cityEntity;
    private IpMappingAttributeEntity traitsAttribute;
    private IpMappingAttributeEntity locationAttribute;
    private IpMappingAttributeEntity postalAttribute;
    private LocationNameEntity locationName;

    @BeforeEach
    void setUp() {
        // Setup Location hierarchy: Continent -> Country -> Subdivision1 -> Subdivision2 -> City
        continentEntity = new LocationEntity();
        continentEntity.setId(1L);
        continentEntity.setLocationCode("AS");
        continentEntity.setGeonameId(6255147L);
        continentEntity.setLocationLevel("CONTINENT");
        continentEntity.setAttributes(Map.of("code", "AS"));
        continentEntity.setParent(null);

        countryEntity = new LocationEntity();
        countryEntity.setId(2L);
        countryEntity.setLocationCode("ID");
        countryEntity.setGeonameId(1643084L);
        countryEntity.setLocationLevel("COUNTRY");
        countryEntity.setAttributes(Map.of("iso_code", "ID"));
        countryEntity.setParent(continentEntity);

        subdivision1Entity = new LocationEntity();
        subdivision1Entity.setId(3L);
        subdivision1Entity.setLocationCode("JK");
        subdivision1Entity.setGeonameId(1642911L);
        subdivision1Entity.setLocationLevel("SUBDIVISION1");
        subdivision1Entity.setAttributes(Map.of("iso_code", "JK"));
        subdivision1Entity.setParent(countryEntity);

        subdivision2Entity = new LocationEntity();
        subdivision2Entity.setId(4L);
        subdivision2Entity.setLocationCode("JKT");
        subdivision2Entity.setGeonameId(1642912L);
        subdivision2Entity.setLocationLevel("SUBDIVISION2");
        subdivision2Entity.setAttributes(Map.of("iso_code", "JKT"));
        subdivision2Entity.setParent(subdivision1Entity);

        cityEntity = new LocationEntity();
        cityEntity.setId(5L);
        cityEntity.setLocationCode("Jakarta");
        cityEntity.setGeonameId(1642588L);
        cityEntity.setLocationLevel("CITY");
        cityEntity.setAttributes(Map.of("name", "Jakarta"));
        cityEntity.setParent(subdivision2Entity);

        // Setup IpMappingEntity
        ipMappingEntity = new IpMappingEntity();
        ipMappingEntity.setId(100L);
        ipMappingEntity.setIpNotation("192.168.1.0/24");
        ipMappingEntity.setScope(Scope.GLOBAL);

        Instant validPeriodInstant =
                LocalDate.parse("2024-01-01").atStartOfDay(ZoneOffset.UTC).toInstant();

        ipMappingEntity.setValidPeriod(validPeriodInstant);
        ipMappingEntity.setLocation(cityEntity);

        // Setup IpMappingAttributeEntity
        traitsAttribute = new IpMappingAttributeEntity();
        traitsAttribute.setId(1L);
        traitsAttribute.setIpMapping(ipMappingEntity);
        traitsAttribute.setObjectName("TRAITS");
        traitsAttribute.setAttributes(Map.of("autonomous_system_number", 12345));

        locationAttribute = new IpMappingAttributeEntity();
        locationAttribute.setId(2L);
        locationAttribute.setIpMapping(ipMappingEntity);
        locationAttribute.setObjectName("LOCATION");
        locationAttribute.setAttributes(Map.of("latitude", -6.2088, "longitude", 106.8456));

        postalAttribute = new IpMappingAttributeEntity();
        postalAttribute.setId(3L);
        postalAttribute.setIpMapping(ipMappingEntity);
        postalAttribute.setObjectName("POSTAL");
        postalAttribute.setAttributes(Map.of("code", "10110"));

        // Setup LocationNameEntity
        locationName = new LocationNameEntity();
        LocationEntity locationEntity = new LocationEntity();
        locationEntity.setId(5L);
        locationName.setLocationNameId(new LocationNameId(1L, "name"));
        locationName.setLocation(cityEntity);
        locationName.setName("Jakarta");
    }

    @Test
    @DisplayName("Should handle null or empty ip mapping entity list")
    void testBuildIpMappingResponseDtoList_NullInput() {
        List<IpMappingResponseDto> result1 =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(null, null, null, null);
        List<IpMappingResponseDto> result2 =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(List.of(), null, null, null);

        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }

    @Test
    @DisplayName("Should handle null attributes list")
    void testBuildIpMappingResponseDtoList_NullAttributes() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);
        List<LocationEntity> locationList = List.of(cityEntity, countryEntity, continentEntity);
        List<LocationNameEntity> nameList = List.of(locationName);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList, null, locationList, nameList);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should handle empty attributes list")
    void testBuildIpMappingResponseDtoList_EmptyAttributes() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);
        List<LocationEntity> locationList = List.of(cityEntity);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList, List.of(), locationList, List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAttributes()).isNull();
    }

    @Test
    @DisplayName("Should handle null locations list")
    void testBuildIpMappingResponseDtoList_NullLocations() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList, List.of(), null, List.of());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should handle null location names list")
    void testBuildIpMappingResponseDtoList_NullLocationNames() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);
        List<LocationEntity> locationList = List.of(cityEntity);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList, List.of(), locationList, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should build location DTO with all hierarchy levels")
    void testBuildIpMappingLocationDto_FullHierarchy() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);
        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList,
                        List.of(),
                        List.of(
                                continentEntity,
                                countryEntity,
                                subdivision1Entity,
                                subdivision2Entity,
                                cityEntity),
                        List.of(locationName));

        assertThat(result).hasSize(1);
        IpMappingLocationDto locationDto = result.get(0).getLocation();
        assertThat(locationDto.getContinent()).isNotNull();
        assertThat(locationDto.getCountry()).isNotNull();
        assertThat(locationDto.getCity()).isNotNull();
        assertThat(locationDto.getAdditionalLocations()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle null child location")
    void testBuildIpMappingLocationDto_NullChild() {
        IpMappingEntity entityWithNullLocation = new IpMappingEntity();
        entityWithNullLocation.setId(200L);
        entityWithNullLocation.setLocation(null);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(entityWithNullLocation), List.of(), List.of(), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isNotNull();
    }

    @Test
    @DisplayName("Should handle location with null level")
    void testBuildIpMappingLocationDto_NullLevel() {
        LocationEntity locationWithNullLevel = new LocationEntity();
        locationWithNullLevel.setId(10L);
        locationWithNullLevel.setLocationLevel(null);
        locationWithNullLevel.setParent(null);

        IpMappingEntity entity = new IpMappingEntity();
        entity.setId(300L);
        entity.setLocation(locationWithNullLevel);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(entity), List.of(), List.of(locationWithNullLevel), List.of());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should prevent circular reference in location hierarchy")
    void testBuildIpMappingLocationDto_CircularReference() {
        LocationEntity loc1 = new LocationEntity();
        loc1.setId(20L);
        loc1.setLocationLevel("COUNTRY");

        LocationEntity loc2 = new LocationEntity();
        loc2.setId(21L);
        loc2.setLocationLevel("CITY");

        loc1.setParent(loc2);
        loc2.setParent(loc1); // circular reference

        IpMappingEntity entity = new IpMappingEntity();
        entity.setId(400L);
        entity.setLocation(loc2);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(entity), List.of(), List.of(loc1, loc2), List.of());

        // should not cause infinite loop
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should convert LocationResponseDto to IpMappingLocationDto")
    void testConvertToIpMappingLocationDto() {
        LocationDto continent = new LocationDto();
        continent.setLocationCode("AS");

        LocationDto country = new LocationDto();
        country.setLocationCode("ID");

        LocationDto city = new LocationDto();
        city.setLocationCode("Jakarta");

        LocationDto subdivision = new LocationDto();
        subdivision.setLocationCode("JK");

        LocationResponseDto locationResponseDto = new LocationResponseDto();
        locationResponseDto.setContinent(continent);
        locationResponseDto.setCountry(country);
        locationResponseDto.setCity(city);
        locationResponseDto.setAdditionalLocations(List.of(subdivision));

        IpMappingResponseDto mappedDto = new IpMappingResponseDto();
        mappedDto.setId(100L);

        when(ipMappingMapper.toDto(any(IpMappingEntity.class))).thenReturn(mappedDto);

        LocationDto registeredCountry = new LocationDto();
        registeredCountry.setLocationCode("ID-REG");

        LocationDto representedCountry = new LocationDto();
        representedCountry.setLocationCode("ID-REP");

        IpMappingResponseDto result =
                ipMappingResponseBuilder.buildIpMappingResponseDto(
                        ipMappingEntity,
                        locationResponseDto,
                        List.of(traitsAttribute),
                        registeredCountry,
                        representedCountry);

        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getLocation().getContinent()).isEqualTo(continent);
        assertThat(result.getLocation().getCountry()).isEqualTo(country);
        assertThat(result.getLocation().getCity()).isEqualTo(city);
        assertThat(result.getLocation().getAdditionalLocations()).hasSize(1);
        assertThat(result.getRegisteredCountry()).isEqualTo(registeredCountry);
        assertThat(result.getRepresentedCountry()).isEqualTo(representedCountry);

        Instant validPeriodInstant =
                LocalDate.parse("2024-01-01").atStartOfDay(ZoneOffset.UTC).toInstant();

        assertThat(result.getValidPeriod()).isEqualTo(validPeriodInstant);
    }

    @Test
    @DisplayName("Should handle duplicate location IDs in grouping")
    void testGroupLocationsById_Duplicates() {
        LocationEntity loc1 = new LocationEntity();
        loc1.setId(1L);
        loc1.setLocationCode("Code1");

        LocationEntity loc1Duplicate = new LocationEntity();
        loc1Duplicate.setId(1L);
        loc1Duplicate.setLocationCode("Code1Updated");

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(ipMappingEntity), List.of(),
                        List.of(loc1, loc1Duplicate), List.of());

        // Should keep first one
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should handle duplicate locale codes in location names")
    void testGroupNamesByLocationId_Duplicates() {
        LocationNameEntity name1 = new LocationNameEntity();
        name1.setLocationNameId(new LocationNameId(1L, "name"));
        name1.setLocation(cityEntity);
        name1.setName("Jakarta");

        LocationNameEntity name1Duplicate = new LocationNameEntity();
        name1Duplicate.setLocationNameId(new LocationNameId(1L, "name"));
        name1Duplicate.setLocation(cityEntity);
        name1Duplicate.setName("Jakarta City");

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(ipMappingEntity), List.of(),
                        List.of(cityEntity), List.of(name1, name1Duplicate));

        // Should keep last one (replacement)
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should handle location DTO with empty names map")
    void testToLocationDto_EmptyNames() {
        LocationEntity location = new LocationEntity();
        location.setLocationCode("TEST");
        location.setGeonameId(123L);
        location.setAttributes(Map.of("key", "value"));

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(ipMappingEntity), List.of(),
                        List.of(location), List.of());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should handle default case in location level switch")
    void testBuildIpMappingLocationDto_UnknownLevel() {
        LocationEntity unknownLevelLocation = new LocationEntity();
        unknownLevelLocation.setId(99L);
        unknownLevelLocation.setLocationCode("UNKNOWN");
        unknownLevelLocation.setLocationLevel(null);
        unknownLevelLocation.setParent(null);

        IpMappingEntity entity = new IpMappingEntity();
        entity.setId(999L);
        entity.setLocation(unknownLevelLocation);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        List.of(entity), List.of(),
                        List.of(unknownLevelLocation), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isNotNull();
    }

    @Test
    @DisplayName("Should build response list with attributes correctly")
    void testBuildIpMappingResponseDtoList_WithAttributes() {
        List<IpMappingEntity> entityList = List.of(ipMappingEntity);
        List<IpMappingAttributeEntity> attrs =
                List.of(traitsAttribute, locationAttribute, postalAttribute);
        List<LocationEntity> locations =
                List.of(
                        continentEntity,
                        countryEntity,
                        subdivision1Entity,
                        subdivision2Entity,
                        cityEntity);
        List<LocationNameEntity> names = List.of(locationName);

        List<IpMappingResponseDto> result =
                ipMappingResponseBuilder.buildIpMappingResponseDtoList(
                        entityList, attrs, locations, names);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
        assertThat(result.get(0).getAttributes()).containsKey("TRAITS");
        assertThat(result.get(0).getAttributes().get("TRAITS"))
                .containsKey("autonomous_system_number");
        assertThat(result.get(0).getAttributes()).containsKey("LOCATION");
        assertThat(result.get(0).getAttributes().get("LOCATION")).containsKey("latitude");
        assertThat(result.get(0).getAttributes()).containsKey("POSTAL");
        assertThat(result.get(0).getAttributes().get("POSTAL")).containsKey("code");
        assertThat(result.get(0).getLocation().getCity()).isNotNull();
        assertThat(result.get(0).getLocation().getCountry()).isNotNull();
        assertThat(result.get(0).getLocation().getContinent()).isNotNull();
        assertThat(result.get(0).getLocation().getAdditionalLocations()).hasSize(2);
    }
}
