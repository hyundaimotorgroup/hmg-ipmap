package com.hmg.ipmap.location;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.location.enums.LocationLevel;
import com.hmg.ipmap.location.exception.LocationNotFoundException;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserRepository;
import com.hmg.ipmap.user.UserService;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Scenario-based test for LocationService.createLocation with isFromLocationRequest false covering
 * all 28 scenarios: - Global Scope (G1-G10): Admin user creating locations - Client Scope (C1-C12):
 * Client user with fallback to Global - SubClient Scope (S1-S13): SubClient user with fallback
 * chain (SubClient → Client → Global)
 */
@ExtendWith(MockitoExtension.class)
class CreateLocationScenarioFromIpMappingTest {

    @Mock private LocationRepository locationRepository;
    @Mock private LocationMapper locationMapper;
    @Mock private LocationNameRepository locationNameRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;

    @InjectMocks private LocationServiceImpl locationService;

    private UserEntity adminUser;
    private UserEntity clientUser;
    private UserEntity subClientUser;
    private AtomicLong idGenerator;
    private Map<Long, LocationEntity> fakeDatabase;

    @BeforeEach
    void setUp() {
        idGenerator = new AtomicLong(1000L);
        fakeDatabase = new HashMap<>();

        // Setup Admin user
        adminUser = new UserEntity();
        adminUser.setId(1L);
        adminUser.setUserType(UserType.ADMIN);

        // Setup Client user
        clientUser = new UserEntity();
        clientUser.setId(2L);
        clientUser.setUserType(UserType.CLIENT);

        // Setup SubClient user
        subClientUser = new UserEntity();
        subClientUser.setId(3L);
        subClientUser.setUserType(UserType.SUB_CLIENT);
        subClientUser.setParent(clientUser);

        // Mock user repository - use lenient
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(clientUser));
        lenient().when(userRepository.findById(3L)).thenReturn(Optional.of(subClientUser));

        // Mock location mapper - use lenient as not all tests use this
        lenient()
                .when(locationMapper.dtoToEntity(any(LocationDto.class)))
                .thenAnswer(
                        inv -> {
                            LocationDto dto = inv.getArgument(0);
                            LocationEntity entity = new LocationEntity();

                            entity.setLocationCode(dto.getLocationCode());
                            entity.setGeonameId(dto.getGeonameId());
                            entity.setAttributes(
                                    dto.getAttributes() != null
                                            ? dto.getAttributes()
                                            : new HashMap<>());
                            return entity;
                        });

        // Mock repository save - assigns PK ID - use lenient
        lenient()
                .when(locationRepository.save(any(LocationEntity.class)))
                .thenAnswer(
                        inv -> {
                            LocationEntity entity = inv.getArgument(0);
                            if (entity.getId() == null) {
                                entity.setId(idGenerator.getAndIncrement());
                            }
                            // Save parent to fakeDatabase if it exists
                            if (entity.getParent() != null
                                    && !fakeDatabase.containsKey(entity.getParent().getId())) {
                                fakeDatabase.put(entity.getParent().getId(), entity.getParent());
                            }
                            fakeDatabase.put(entity.getId(), entity);
                            return entity;
                        });

        // Mock repository findById - use lenient
        lenient()
                .when(locationRepository.findById(anyLong()))
                .thenAnswer(
                        inv -> {
                            Long id = inv.getArgument(0);
                            return Optional.ofNullable(fakeDatabase.get(id));
                        });

        // Mock findLocationByGeonameIdAndScope - use lenient (single result)
        lenient()
                .when(locationRepository.findLocationByGeonameIdAndScope(anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            Long geonameId = inv.getArgument(0);
                            Scope scope = inv.getArgument(1);
                            return fakeDatabase.values().stream()
                                    .filter(
                                            location ->
                                                    location.getGeonameId().equals(geonameId)
                                                            && location.getScope().equals(scope))
                                    .findFirst();
                        });

        // Mock findLocationByGeonameIdInAndUserId - use lenient (filters by geonameIds AND userId)
        lenient()
                .when(locationRepository.findLocationByGeonameIdInAndUserId(anyList(), any()))
                .thenAnswer(
                        inv -> {
                            List<Long> geonameIds = inv.getArgument(0);
                            Long userId = inv.getArgument(1);
                            return fakeDatabase.values().stream()
                                    .filter(
                                            location ->
                                                    geonameIds.contains(location.getGeonameId())
                                                            && location.getUser()
                                                                    .getId()
                                                                    .equals(userId))
                                    .toList();
                        });

        // Mock findLocationByGeonameIdInAndScope - use lenient (filters by geonameIds AND scope)
        lenient()
                .when(locationRepository.findLocationByGeonameIdInAndScope(anyList(), any()))
                .thenAnswer(
                        inv -> {
                            List<Long> geonameIds = inv.getArgument(0);
                            Scope scope = inv.getArgument(1);
                            return fakeDatabase.values().stream()
                                    .filter(
                                            location ->
                                                    geonameIds.contains(location.getGeonameId())
                                                            && location.getScope().equals(scope))
                                    .toList();
                        });

        // Mock location name repository - use lenient
        lenient()
                .when(locationNameRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(locationNameRepository.findAllByLocationId(anyLong())).thenReturn(List.of());

        // Mock user service - use lenient
        lenient().doNothing().when(userService).checkUserAccess(any(), any());

        // Mock findHierarchyWithNames - walks parent chain in fakeDatabase (self + all ancestors)
        lenient()
                .when(locationRepository.findHierarchyWithNames(anyLong()))
                .thenAnswer(
                        inv -> {
                            Long id = inv.getArgument(0);
                            List<LocationEntity> hierarchy = new ArrayList<>();
                            LocationEntity current = fakeDatabase.get(id);
                            while (current != null) {
                                hierarchy.add(current);
                                Long parentId =
                                        current.getParent() != null
                                                ? current.getParent().getId()
                                                : null;
                                current = parentId != null ? fakeDatabase.get(parentId) : null;
                            }
                            return hierarchy;
                        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        fakeDatabase.clear();
    }

    // ==================== HELPER METHODS ====================

    private LocationDto buildLocationDto(String code, Long geonameId) {
        LocationDto dto = new LocationDto();

        dto.setLocationCode(code);
        dto.setGeonameId(geonameId);
        dto.setNames(new HashMap<>());
        dto.setAttributes(new HashMap<>());
        return dto;
    }

    private LocationDto buildLocationDto(Long geonameId) {
        LocationDto dto = new LocationDto();
        dto.setGeonameId(geonameId);
        return dto;
    }

    // Overload with geonameId and attributes for exact matching
    private LocationEntity createExistingLocationEntity(
            String code,
            String level,
            Scope scope,
            UserEntity user,
            LocationEntity parent,
            Long geonameId,
            Map<String, Object> attributes) {
        LocationEntity entity = new LocationEntity();
        entity.setId(idGenerator.getAndIncrement());

        entity.setLocationCode(code);
        entity.setLocationLevel(level);
        entity.setScope(scope);
        entity.setUser(user);
        entity.setParent(parent);
        entity.setGeonameId(geonameId);
        entity.setAttributes(attributes != null ? attributes : new HashMap<>());
        entity.setNames(List.of());

        if (parent != null && !fakeDatabase.containsKey(parent.getId())) {
            fakeDatabase.put(parent.getId(), parent);
        }
        fakeDatabase.put(entity.getId(), entity);
        return entity;
    }

    // Convenience overload with just geonameId (attributes defaults to empty)
    private LocationEntity createExistingLocationEntity(
            String code,
            String level,
            Scope scope,
            UserEntity user,
            LocationEntity parent,
            Long geonameId) {
        return createExistingLocationEntity(
                code, level, scope, user, parent, geonameId, new HashMap<>());
    }

    private void mockExistingLocation(LocationEntity entity) {
        lenient()
                .when(
                        locationRepository.findLocationByGeonameIdAndUserId(
                                entity.getGeonameId(), entity.getUser().getId()))
                .thenReturn(Optional.of(entity));
    }

    private void mockNoExistingLocation(Long geonameId, Long userId) {
        lenient()
                .when(locationRepository.findLocationByGeonameIdAndUserId(geonameId, userId))
                .thenReturn(Optional.empty());
    }

    // ==================== GLOBAL SCOPE SCENARIOS (G1-G8) ====================

    @Nested
    @DisplayName("Global Scope Scenarios (Admin)")
    class GlobalScopeScenarios {

        @BeforeEach
        void setupGlobalContext() {
            UserContextHolder.set(
                    new UserContext(
                            1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));
        }

        @Test
        @DisplayName("G1: All are new → Create all (City, Country, Continent)")
        void testG1_AllNew_CreateAll() {
            // Given: All locations are new
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            mockNoExistingLocation(1L, adminUser.getId());
            mockNoExistingLocation(2L, adminUser.getId());
            mockNoExistingLocation(3L, adminUser.getId());

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            assertNotNull(response.get(DefaultLocationLevel.CONTINENT));
            assertNotNull(response.get(DefaultLocationLevel.COUNTRY));
            assertNotNull(response.get(DefaultLocationLevel.CITY));

            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify proper hierarchy: Continent → Country → City
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G2: Only City exists → Update City, Create Country & Continent")
        void testG2_OnlyCityExists_UpdateCityCreateRest() {
            // Given: City exists without proper parent hierarchy
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, null, 3L);

            mockExistingLocation(existingCity);
            mockNoExistingLocation(2L, 1L);
            mockNoExistingLocation(1L, 1L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (no parent) → Country (parent: Continent) → City (parent:
            // Country)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G3: City & Country exist → Update both, Create Continent")
        void testG3_CityAndCountryExist_UpdateBothCreateContinent() {
            // Given: City & Country exist with proper hierarchy, but need new Continent parent
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, null, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingCountry, 3L);

            mockExistingLocation(existingCity);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(1L, 1L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country → City
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName(("G4: Only ID Exist, lookup only"))
        void testG4_IDOnlyExist_LookupAll() {
            // Given: All locations exist with proper hierarchy
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    continent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    country.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(), city.getGeonameId());
        }

        @Test
        @DisplayName(("G5: Only ID Exist, lookup not exist -> throw exception"))
        void testG5_IDOnlyExist_LookupNotExist_shouldThrowException() {
            // Given: All locations exist with proper hierarchy

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            assertThrows(
                    LocationNotFoundException.class,
                    () -> locationService.createLocation(adminUser, locationMap, false));
        }

        @Test
        @DisplayName("G6: All exist → Update all")
        void testG6_AllExist_UpdateAll() {
            // Given: All locations exist with proper hierarchy
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify updates occurred and hierarchy maintained
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G7: Only Country & Continent exist → Create City, Rest updated")
        void testG7_CountryAndContinentExist_CreateCityUpdateRest() {
            // Given: Country & Continent exist with proper hierarchy
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingContinent, 2L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 1L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify City was created with Country as parent
            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals("CITY", savedCity.getLocationLevel());
            assertNotNull(savedCity.getParent());
            assertEquals(existingContinent.getId(), existingCountry.getParent().getId());
            assertEquals(existingCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G8: Only Continent exists → Create City & Country, Continent not update")
        void testG8_OnlyContinentExists_CreateCityAndCountryNoUpdateContinent() {
            // Given: Continent exists
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 1L);
            mockNoExistingLocation(3L, 1L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (new)
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G9: City & Continent exist → Update both, Create Country")
        void testG9_CityAndContinentExist_UpdateBothCreateCountry() {
            // Given: City & Continent exist, but City needs Country parent
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, null, 3L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 1L);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (realigned)
            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(existingContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("G10: Only Country exists → Create City & Continent, Update Country")
        void testG9_OnlyCountryExists_CreateCityAndContinentUpdateCountry() {
            // Given: Country exists without parent
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, null, 2L);

            mockNoExistingLocation(1L, 1L);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 1L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(adminUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (new) → Country (realigned) → City (new)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }
    }

    // ==================== CLIENT SCOPE SCENARIOS (C1-C8) ====================

    @Nested
    @DisplayName("Client Scope Scenarios")
    class ClientScopeScenarios {

        @BeforeEach
        void setupClientContext() {
            UserContextHolder.set(
                    new UserContext(
                            2L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null));
        }

        @Test
        @DisplayName("C1: All are new → Fallback to Global")
        void testC1_AllNew_FallbackToGlobal() {
            // Given: All locations are new in Client scope
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            mockNoExistingLocation(1L, clientUser.getId());
            mockNoExistingLocation(2L, clientUser.getId());
            mockNoExistingLocation(3L, clientUser.getId());

            LocationEntity existingGlobalContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingGlobalCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingGlobalContinent, 2L);
            LocationEntity existingGlobalCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingGlobalCountry, 3L);

            mockExistingLocation(existingGlobalContinent);
            mockExistingLocation(existingGlobalCountry);
            mockExistingLocation(existingGlobalCity);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            // Verify all get global SCOPE
            fakeDatabase.values().forEach(entity -> assertEquals(Scope.GLOBAL, entity.getScope()));
        }

        @Test
        @DisplayName("C2: All are new → create in Client scope")
        void testC2_AllNew_CreateInClientScope() {
            // Given: All locations are new in Client scope
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            // mock no existing location in client scope
            mockNoExistingLocation(1L, clientUser.getId());
            mockNoExistingLocation(2L, clientUser.getId());
            mockNoExistingLocation(3L, clientUser.getId());

            // mock no existing location in global scope
            mockNoExistingLocation(1L, adminUser.getId());
            mockNoExistingLocation(2L, adminUser.getId());
            mockNoExistingLocation(3L, adminUser.getId());

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then create all location
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify all created in CLIENT scope
            fakeDatabase.values().forEach(entity -> assertEquals(Scope.CLIENT, entity.getScope()));
        }

        @Test
        @DisplayName("C3: Only City exists → Update City, Create Country & Continent")
        void testC3_OnlyCityExists_UpdateCityCreateRest() {
            // Given: City exists without proper parent hierarchy in Client scope
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, null, 3L);

            mockExistingLocation(existingCity);
            mockNoExistingLocation(2L, clientUser.getId());
            mockNoExistingLocation(1L, clientUser.getId());

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (no parent) → Country (parent: Continent) → City (parent:
            // Country)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("C4: City & Country exist → Update both, Create Continent")
        void testC4_CityAndCountryExist_UpdateBothCreateContinent() {
            // Given: City & Country exist with proper hierarchy in Client scope
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, null, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, existingCountry, 3L);

            mockExistingLocation(existingCity);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(1L, clientUser.getId());

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country → City
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("C5: Only ID Exist, lookup only")
        void testC5_IDOnlyExist_LookupOnly() {
            // Given: All locations exist with proper hierarchy in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            // Verify response build the structure with existing data
            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    continent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    country.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(), city.getGeonameId());
        }

        @Test
        @DisplayName("C6: Only ID Exist, Not Found in client -> Found in global")
        void testC6_IDOnlyExist_NotFoundInClient_FoundInGlobal() {
            // Given: All locations exist with proper hierarchy in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            // Verify response build the structure with existing data
            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    continent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    country.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(), city.getGeonameId());
        }

        @Test
        @DisplayName(
                ("C7: Only ID Exist, lookup not exist in CLIENT and GLOBAL -> throw exception"))
        void testC7_IDOnlyExist_LookupNotExistInClientAndGlobal_shouldThrowException() {
            // Given: All locations exist with proper hierarchy

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            assertThrows(
                    LocationNotFoundException.class,
                    () -> locationService.createLocation(adminUser, locationMap, false));
        }

        @Test
        @DisplayName("C8: All exist → Update all")
        void testC8_AllExist_UpdateAll() {
            // Given: All locations exist with proper hierarchy in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify updates occurred and hierarchy maintained
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());
            assertEquals(Scope.CLIENT, savedContinent.getScope());
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());
            assertEquals(Scope.CLIENT, savedCountry.getScope());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
            assertEquals(Scope.CLIENT, savedCity.getScope());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("C9 Country & Continent exist → Create City, Update rest")
        void testC9_CountryAndContinentExist_CreateCityUpdateRest() {
            // Given: Country & Continent exist with proper hierarchy in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP-UPDATED",
                            "COUNTRY",
                            Scope.CLIENT,
                            clientUser,
                            existingContinent,
                            2L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 2L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify City was created with Country as parent
            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals("CITY", savedCity.getLocationLevel());
            assertNotNull(savedCity.getParent());
            assertEquals(existingCountry.getId(), savedCity.getParent().getId());

            // Verify Country Update
            LocationEntity updatedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(updatedCountry);
            assertEquals("JP-UPDATED", updatedCountry.getLocationCode());
        }

        @Test
        @DisplayName("C10: Only Continent exists → Create City & Country, Update Continent")
        void testC10_OnlyContinentExists_CreateCityAndCountryUpdateContinent() {
            // Given: Continent exists in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 2L);
            mockNoExistingLocation(3L, 2L);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (new)
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertNull(savedContinent.getParent());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("C11: City & Continent exist → Update both, Create Country")
        void testC11_CityAndContinentExist_UpdateBothCreateCountry() {
            // Given: City & Continent exist in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, null, 3L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 2L);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (realigned)
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertNotNull(savedContinent);
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(existingContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
        }

        @Test
        @DisplayName("C12: Only Country exists → Create City & Continent, Update Country")
        void testC12_OnlyCountryExists_CreateCityAndContinentUpdateCountry() {
            // Given: Country exists in Client scope
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, null, 2L);

            mockNoExistingLocation(1L, 2L);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 2L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (new) → Country (realigned) → City (new)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }
    }

    // ==================== SUBCLIENT SCOPE SCENARIOS (S1-S8) ====================

    @Nested
    @DisplayName("Subclient Scope Scenarios")
    class SubclientScopeScenarios {

        @BeforeEach
        void setupSubclientContext() {
            UserContextHolder.set(
                    new UserContext(
                            3L,
                            "sub_client",
                            UserType.SUB_CLIENT,
                            "1.2.3.4",
                            Scope.SUB_CLIENT,
                            null,
                            null));
        }

        @Test
        @DisplayName("S1: All are new → Found in Client")
        void testS1_AllNew_FallbackToClient() {
            // Given
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            mockNoExistingLocation(1L, subClientUser.getId());
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(3L, subClientUser.getId());

            LocationEntity exitingClientContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingClientCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, null, 2L);
            LocationEntity exitingClientCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, null, 3L);

            mockExistingLocation(exitingClientContinent);
            mockExistingLocation(existingClientCountry);
            mockExistingLocation(exitingClientCity);

            // Mock: Not found in Client scope
            mockNoExistingLocation(1L, subClientUser.getId());
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(3L, subClientUser.getId());

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    exitingClientContinent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    existingClientCountry.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(),
                    exitingClientCity.getGeonameId());
        }

        @Test
        @DisplayName("S2: All are new → Fallback to Client (not found) -> Fallback to Global")
        void testS2_AllNew_FallbackToGlobal() {
            // Given
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            // Mock Not found in subClient scope
            mockNoExistingLocation(1L, subClientUser.getId());
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(3L, subClientUser.getId());

            // Mock Not found in client scope
            mockNoExistingLocation(1L, clientUser.getId());
            mockNoExistingLocation(2L, clientUser.getId());
            mockNoExistingLocation(3L, clientUser.getId());

            LocationEntity exitingClientContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.CLIENT, clientUser, null, 1L);
            LocationEntity existingClientCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.CLIENT, clientUser, null, 2L);
            LocationEntity exitingClientCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.CLIENT, clientUser, null, 3L);

            // Mock Found in global scope
            mockExistingLocation(exitingClientContinent);
            mockExistingLocation(existingClientCountry);
            mockExistingLocation(exitingClientCity);

            // Mock: Not found in Client scope
            mockNoExistingLocation(1L, subClientUser.getId());
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(3L, subClientUser.getId());

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    exitingClientContinent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    existingClientCountry.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(),
                    exitingClientCity.getGeonameId());
        }

        @Test
        @DisplayName(
                "S3: All are new → Fallback Client (not found) -> Fallback Global (not found) -> create in SubClient scope")
        void testS3_AllNew_FallbackThenCreateInSubClientScope() {
            // Given
            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            // Mock Not found in subClient scope
            mockNoExistingLocation(1L, subClientUser.getId());
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(3L, subClientUser.getId());

            // Mock Not found in client scope
            mockNoExistingLocation(1L, clientUser.getId());
            mockNoExistingLocation(2L, clientUser.getId());
            mockNoExistingLocation(3L, clientUser.getId());

            // Mock Not found in global scope
            mockNoExistingLocation(1L, adminUser.getId());
            mockNoExistingLocation(2L, adminUser.getId());
            mockNoExistingLocation(3L, adminUser.getId());

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (new) → Country (realigned) → City (new)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("S4: Only City exists → Update City, Create Country & Continent")
        void testS2_OnlyCityExists_UpdateCityCreateRest() {
            // Given: City exists without proper parent hierarchy in Subclient scope
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.SUB_CLIENT, subClientUser, null, 3L);

            mockExistingLocation(existingCity);
            mockNoExistingLocation(2L, subClientUser.getId());
            mockNoExistingLocation(1L, subClientUser.getId());

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (no parent) → Country (parent: Continent) → City (parent:
            // Country)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("S5: City & Country exist → Update both, Create Continent")
        void testS5_CityAndCountryExist_UpdateBothCreateContinent() {
            // Given: City & Country exist with proper hierarchy in Subclient scope
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.SUB_CLIENT, subClientUser, null, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.SUB_CLIENT, subClientUser, existingCountry, 3L);

            mockExistingLocation(existingCity);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(1L, 3L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country → City
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
        }

        @Test
        @DisplayName("S6: All exist → Update all")
        void testS6_AllExist_UpdateAll() {
            // Given: All locations exist with proper hierarchy in Subclient scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.SUB_CLIENT, subClientUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP",
                            "COUNTRY",
                            Scope.SUB_CLIENT,
                            subClientUser,
                            existingContinent,
                            2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.SUB_CLIENT, subClientUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify updates occurred and hierarchy maintained
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("S7: Only ID Exist, Not Found in client -> Found in client or global")
        void testS7_IDOnlyExist_NotFoundInSubClient_FoundInClientOrGlobal() {
            // Given: All locations exist with proper hierarchy in Client scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.GLOBAL, adminUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.GLOBAL, adminUser, existingContinent, 2L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.GLOBAL, adminUser, existingCountry, 3L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(clientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(0)).save(any(LocationEntity.class));

            // Verify response build the structure with existing data
            assertEquals(
                    response.get(DefaultLocationLevel.CONTINENT).getGeonameId(),
                    continent.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.COUNTRY).getGeonameId(),
                    country.getGeonameId());
            assertEquals(
                    response.get(DefaultLocationLevel.CITY).getGeonameId(), city.getGeonameId());
        }

        @Test
        @DisplayName(
                ("S8: Only ID Exist, lookup not exist in SUB CLIENT, CLIENT and GLOBAL -> throw exception"))
        void testS8_IDOnlyExist_LookupNotExistInSubClientClientAndGlobal_shouldThrowException() {
            // Given: All locations exist with proper hierarchy

            LocationDto continent = buildLocationDto(1L);
            LocationDto country = buildLocationDto(2L);
            LocationDto city = buildLocationDto(3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            assertThrows(
                    LocationNotFoundException.class,
                    () -> locationService.createLocation(adminUser, locationMap, false));
        }

        @Test
        @DisplayName("S9: Country & Continent exist → Create City, Update rest")
        void testS9_CountryAndContinentExist_CreateCityUpdateRest() {
            // Given: Country & Continent exist with proper hierarchy in Subclient scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.SUB_CLIENT, subClientUser, null, 1L);
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP",
                            "COUNTRY",
                            Scope.SUB_CLIENT,
                            subClientUser,
                            existingContinent,
                            2L);

            mockExistingLocation(existingContinent);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 3L);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP-UPDATED", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify continent and country updated
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertEquals("JP-UPDATED", savedCountry.getLocationCode());

            // Verify City was created with Country as parent
            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals("CITY", savedCity.getLocationLevel());
            assertNotNull(savedCity.getParent());
            assertEquals(existingCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("S10: Only Continent exists → Create City & Country, Update Continent")
        void testS10_OnlyContinentExists_CreateCityAndCountryUpdateContinent() {
            // Given: Continent exists in Subclient scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.SUB_CLIENT, subClientUser, null, 1L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 3L);
            mockNoExistingLocation(3L, 3L);

            LocationDto continent = buildLocationDto("AS-UPDATED", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (new)
            LocationEntity savedContinent = fakeDatabase.get(existingContinent.getId());
            assertNull(savedContinent.getParent());
            assertEquals("AS-UPDATED", savedContinent.getLocationCode());

            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }

        @Test
        @DisplayName("S11: City & Continent exist → Update both, Create Country")
        void testS11_CityAndContinentExist_UpdateBothCreateCountry() {
            // Given: City & Continent exist in Subclient scope
            LocationEntity existingContinent =
                    createExistingLocationEntity(
                            "AS", "CONTINENT", Scope.SUB_CLIENT, subClientUser, null, 1L);
            LocationEntity existingCity =
                    createExistingLocationEntity(
                            "Tokyo", "CITY", Scope.SUB_CLIENT, subClientUser, null, 3L);

            mockExistingLocation(existingContinent);
            mockNoExistingLocation(2L, 3L);
            mockExistingLocation(existingCity);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo-UPDATED", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent → Country (new) → City (realigned)
            LocationEntity savedCountry =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(2L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCountry);
            assertEquals(existingContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity = fakeDatabase.get(existingCity.getId());
            assertNotNull(savedCity);
            assertNotNull(savedCity.getParent());
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
            assertEquals("Tokyo-UPDATED", savedCity.getLocationCode());
        }

        @Test
        @DisplayName("S12: Only Country exists → Create City & Continent, Update Country")
        void testS12_OnlyCountryExists_CreateCityAndContinentUpdateCountry() {
            // Given: Country exists in Subclient scope
            LocationEntity existingCountry =
                    createExistingLocationEntity(
                            "JP", "COUNTRY", Scope.SUB_CLIENT, subClientUser, null, 2L);

            mockNoExistingLocation(1L, 3L);
            mockExistingLocation(existingCountry);
            mockNoExistingLocation(3L, 3L);

            LocationDto continent = buildLocationDto("AS", 1L);
            LocationDto country = buildLocationDto("JP", 2L);
            LocationDto city = buildLocationDto("Tokyo", 3L);

            Map<LocationLevel, LocationDto> locationMap =
                    buildLocationMap(continent, country, city);

            // When
            Map<LocationLevel, LocationDto> response =
                    locationService.createLocation(subClientUser, locationMap, false);

            // Then
            assertNotNull(response);
            verify(locationRepository, times(3)).save(any(LocationEntity.class));

            // Verify hierarchy: Continent (new) → Country (realigned) → City (new)
            LocationEntity savedContinent =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(1L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedContinent);
            assertNull(savedContinent.getParent());

            LocationEntity savedCountry = fakeDatabase.get(existingCountry.getId());
            assertNotNull(savedCountry);
            assertNotNull(savedCountry.getParent());
            assertEquals(savedContinent.getId(), savedCountry.getParent().getId());

            LocationEntity savedCity =
                    fakeDatabase.values().stream()
                            .filter(e -> e.getGeonameId().equals(3L))
                            .findFirst()
                            .orElse(null);
            assertNotNull(savedCity);
            assertEquals(savedCountry.getId(), savedCity.getParent().getId());
        }
    }

    private Map<LocationLevel, LocationDto> buildLocationMap(
            LocationDto continent, LocationDto country, LocationDto city) {
        Map<LocationLevel, LocationDto> map = new java.util.LinkedHashMap<>();
        map.put(DefaultLocationLevel.CONTINENT, continent);
        map.put(DefaultLocationLevel.COUNTRY, country);
        map.put(DefaultLocationLevel.REGION, null);
        map.put(DefaultLocationLevel.CITY, city);
        return map;
    }
}
