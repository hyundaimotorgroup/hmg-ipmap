package com.hmg.ipmap.ingestion.file.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;
import com.hmg.ipmap.ingestion.file.job.model.CityTerritory;
import com.hmg.ipmap.ingestion.file.job.model.ContinentTerritory;
import com.hmg.ipmap.ingestion.file.job.model.CountryTerritory;
import com.hmg.ipmap.ingestion.file.job.model.DefaultLocation;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationService;
import com.hmg.ipmap.user.UserEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationIngestionServiceTest {

    @Mock private LocationRepository locationRepository;

    @Mock private LocationNameRepository locationNameRepository;

    @Mock private JobParameter jobParameter;

    @Mock private BatchFileDetailRepository batchFileDetailRepository;

    @Mock private LocationService locationService;

    @Mock private IngestionCacheService ingestionCacheService;

    @Captor private ArgumentCaptor<LocationEntity> locationEntityCaptor;

    @Captor private ArgumentCaptor<LocationNameEntity> locationNameEntityCaptor;

    @InjectMocks private DefaultLocationIngestionServiceImpl locationIngestionService;

    @BeforeEach
    void setUp() {
        UserEntity executor = new UserEntity();
        executor.setUserType(UserType.ADMIN);
        executor.setId(1L);

        when(jobParameter.getExecutor()).thenReturn(executor);
        AtomicLong idSequence = new AtomicLong(1L);
        when(locationRepository.save(any(LocationEntity.class)))
                .thenAnswer(
                        inv -> {
                            LocationEntity entity = inv.getArgument(0);
                            if (entity.getId() == null) {
                                entity.setId(idSequence.getAndIncrement());
                            }
                            return entity;
                        });
        when(locationNameRepository.save(any(LocationNameEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(locationRepository.findByLocationCodeAndLocationLevel(any(), any()))
                .thenReturn(Optional.empty());
        when(locationRepository.mergeAllLocations(any())).thenReturn(new ArrayList<>());
        when(locationRepository.saveAllCityLocations(any())).thenReturn(new ArrayList<>());
        when(locationService.isUpdateNeeded(any(), any())).thenReturn(false);
    }

    @Test
    void testRegisterLocation_ShouldRegisterAllLocationItemsWithContinentCountryAndCity() {
        ContinentTerritory continent =
                ContinentTerritory.builder()
                        .continentCode("AS")
                        .continentName("Asia")
                        .isInEuropeanUnion(false)
                        .build();

        CountryTerritory country =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(1835841L)
                        .isInEuropeanUnion(false)
                        .build();

        CityTerritory city =
                CityTerritory.builder()
                        .cityName("Seoul")
                        .geonameId(1835847L)
                        .isInEuropeanUnion(false)
                        .build();

        DefaultLocation location1 =
                DefaultLocation.builder()
                        .continent(continent)
                        .country(country)
                        .city(city)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        ContinentTerritory continent2 =
                ContinentTerritory.builder()
                        .continentCode("EU")
                        .continentName("Europe")
                        .isInEuropeanUnion(true)
                        .build();

        CountryTerritory country2 =
                CountryTerritory.builder()
                        .countryIsoCode("FR")
                        .countryName("France")
                        .geonameId(2988389L)
                        .isInEuropeanUnion(true)
                        .build();

        CityTerritory city2 =
                CityTerritory.builder()
                        .cityName("Paris")
                        .geonameId(2988507L)
                        .isInEuropeanUnion(true)
                        .build();

        DefaultLocation location2 =
                DefaultLocation.builder()
                        .continent(continent2)
                        .country(country2)
                        .city(city2)
                        .fileDetailId(2L)
                        .localeCode("en")
                        .build();

        List<DefaultLocation> locations = List.of(location1, location2);

        locationIngestionService.registerLocation(locations);

        verify(batchFileDetailRepository).updateAllToSuccessInBatch(any());
        verify(locationRepository, atLeast(2)).save(locationEntityCaptor.capture());
        verify(locationNameRepository, atLeast(2)).save(locationNameEntityCaptor.capture());

        assertThat(locationEntityCaptor.getAllValues()).isNotEmpty();
        assertThat(locationNameEntityCaptor.getAllValues()).isNotEmpty();
    }

    @Test
    void testRegisterLocation_ShouldNotSaveWhenNoLocationItemsProvided() {

        locationIngestionService.registerLocation(new ArrayList<>());

        verify(locationRepository, Mockito.never()).save(any(LocationEntity.class));
        verify(locationRepository, Mockito.never()).saveAllCityLocations(any());
        verify(locationRepository, Mockito.never()).upsertAllLocationNames(any(), any());
        verify(batchFileDetailRepository, Mockito.never()).updateAllToSuccessInBatch(any());
    }

    @Test
    void testRegisterLocation_ShouldNotSaveWhenContinentIsNull() {
        CountryTerritory country =
                CountryTerritory.builder()
                        .countryIsoCode("XX")
                        .countryName("TestCountry")
                        .geonameId(999999L)
                        .isInEuropeanUnion(false)
                        .build();

        CityTerritory city =
                CityTerritory.builder()
                        .cityName("TestCity")
                        .geonameId(888888L)
                        .isInEuropeanUnion(false)
                        .build();

        DefaultLocation locationWithoutContinent =
                DefaultLocation.builder()
                        .continent(null)
                        .country(country)
                        .city(city)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        locationIngestionService.registerLocation(List.of(locationWithoutContinent));

        verify(locationRepository, Mockito.never()).save(any(LocationEntity.class));
        verify(locationRepository, Mockito.never()).saveAllCityLocations(any());
        verify(locationRepository, Mockito.never()).upsertAllLocationNames(any(), any());
        verify(batchFileDetailRepository, Mockito.never()).updateAllToSuccessInBatch(any());
    }

    @Test
    void testRegisterLocation_ShouldNotSaveWhenAllItemsHaveNullContinent() {
        CountryTerritory country1 =
                CountryTerritory.builder()
                        .countryIsoCode("XX")
                        .countryName("Country1")
                        .geonameId(111111L)
                        .isInEuropeanUnion(false)
                        .build();

        CountryTerritory country2 =
                CountryTerritory.builder()
                        .countryIsoCode("YY")
                        .countryName("Country2")
                        .geonameId(222222L)
                        .isInEuropeanUnion(false)
                        .build();

        DefaultLocation item1 =
                DefaultLocation.builder()
                        .continent(null)
                        .country(country1)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        DefaultLocation item2 =
                DefaultLocation.builder()
                        .continent(null)
                        .country(country2)
                        .fileDetailId(2L)
                        .localeCode("en")
                        .build();

        locationIngestionService.registerLocation(List.of(item1, item2));

        verify(locationRepository, Mockito.never()).save(any(LocationEntity.class));
        verify(locationRepository, Mockito.never()).saveAllCityLocations(any());
        verify(locationRepository, Mockito.never()).upsertAllLocationNames(any(), any());
        verify(batchFileDetailRepository, Mockito.never()).updateAllToSuccessInBatch(any());
    }

    @Test
    void testRegisterLocation_ShouldNotSaveWhenLocationLevelIsNull() {
        DefaultLocation locationWithNullLevel =
                DefaultLocation.builder()
                        .continent(null)
                        .country(null)
                        .city(null)
                        .region(null)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        locationIngestionService.registerLocation(List.of(locationWithNullLevel));

        verify(locationRepository, Mockito.never()).save(any(LocationEntity.class));
        verify(locationRepository, Mockito.never()).saveAllCityLocations(any());
        verify(locationRepository, Mockito.never()).upsertAllLocationNames(any(), any());
        verify(batchFileDetailRepository, Mockito.never()).updateAllToSuccessInBatch(any());
    }

    @Test
    void testRegisterLocation_ShouldProcessOnlyWhenContinentExists() {
        ContinentTerritory continent =
                ContinentTerritory.builder()
                        .continentCode("AS")
                        .continentName("Asia")
                        .isInEuropeanUnion(false)
                        .build();

        CountryTerritory country =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(1835841L)
                        .isInEuropeanUnion(false)
                        .build();

        DefaultLocation validItem1 =
                DefaultLocation.builder()
                        .continent(continent)
                        .country(country)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        DefaultLocation validItem2 =
                DefaultLocation.builder()
                        .continent(continent)
                        .country(country)
                        .fileDetailId(2L)
                        .localeCode("en")
                        .build();

        locationIngestionService.registerLocation(List.of(validItem1, validItem2));

        // Assert - Should proceed because continent exists in the list
        // and save locations to database
        verify(locationRepository, atLeast(1)).save(any(LocationEntity.class));
        verify(batchFileDetailRepository).updateAllToSuccessInBatch(any());
    }

    @Test
    void testRegisterLocation_ShouldNotCallBatchServiceWhenExtractionFails() {
        ContinentTerritory continent =
                ContinentTerritory.builder()
                        .continentCode("AS")
                        .continentName("Asia")
                        .isInEuropeanUnion(false)
                        .build();

        CountryTerritory country =
                CountryTerritory.builder()
                        .countryIsoCode("KR")
                        .countryName("South Korea")
                        .geonameId(1835841L)
                        .isInEuropeanUnion(false)
                        .build();

        DefaultLocation location =
                DefaultLocation.builder()
                        .continent(continent)
                        .country(country)
                        .fileDetailId(1L)
                        .localeCode("en")
                        .build();

        doThrow(new RuntimeException("Database connection error"))
                .when(ingestionCacheService)
                .preloadCache(
                        ArgumentMatchers.<LocationProcessingContext<? extends BaseLocation>>any());

        List<DefaultLocation> items = List.of(location);
        RuntimeException thrown =
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> locationIngestionService.registerLocation(items));

        assertThat(thrown).isNotNull();
        verify(batchFileDetailRepository, Mockito.never()).updateAllToSuccessInBatch(any());
    }
}
