package com.hmg.ipmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationMapper;
import com.hmg.ipmap.location.LocationNameMapper;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationServiceImpl;
import com.hmg.ipmap.location.dto.BaseLocationRequestDto;
import com.hmg.ipmap.location.dto.DefaultLocationRequestDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.location.enums.LocationLevel;
import com.hmg.ipmap.location.exception.LocationAlreadyExistException;
import com.hmg.ipmap.location.exception.LocationNotFoundException;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserRepository;
import com.hmg.ipmap.user.UserService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock private LocationRepository locationRepository;
    @Mock private LocationMapper locationMapper;
    @Mock private LocationNameRepository locationNameRepository;
    @Mock private UserRepository userRepository;
    @Mock private IpMappingRepository ipMappingRepository;

    @Mock
    @SuppressWarnings("unused")
    private UserService userService;

    @Mock
    @SuppressWarnings("unused")
    private LocationNameMapper locationNameMapper;

    @InjectMocks private LocationServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setUserType(UserType.ADMIN);

        UserContextHolder.set(
                new UserContext(1L, "user", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    // ==========================================================
    // delete
    // ==========================================================
    @Nested
    class DeleteTests {

        @Test
        void success_deleteLocation() {
            LocationEntity entity = new LocationEntity();
            entity.setId(10L);
            entity.setUser(user);

            when(locationRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(locationRepository.findLocationParentAndChildren(10L)).thenReturn(List.of(entity));
            when(ipMappingRepository.findAllByLocationIdIn(any())).thenReturn(List.of());
            when(locationRepository.findByParent(entity)).thenReturn(List.of());

            service.delete(10L);

            verify(locationNameRepository).deleteByLocationId(10L);
            verify(locationRepository).deleteById(10L);
        }

        @Test
        void deleteLocation_usedByIpMapping_throwsException() {
            LocationEntity entity = new LocationEntity();
            entity.setId(10L);
            entity.setUser(user);

            when(locationRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(locationRepository.findLocationParentAndChildren(10L)).thenReturn(List.of(entity));
            when(ipMappingRepository.findAllByLocationIdIn(any()))
                    .thenReturn(List.of(new IpMappingEntity()));

            assertThatThrownBy(() -> service.delete(10L))
                    .isInstanceOf(LocationAlreadyExistException.class);
        }
    }

    // ==========================================================
    // findLocationHierarchy
    // ==========================================================
    @Nested
    class FindLocationHierarchyTests {

        @Test
        void success_buildHierarchy() {

            LocationEntity city = new LocationEntity();
            city.setId(10L);
            city.setLocationLevel("CITY");
            city.setNames(List.of());

            LocationEntity country = new LocationEntity();
            country.setId(5L);
            country.setLocationLevel("COUNTRY");
            country.setNames(List.of());

            when(locationRepository.findHierarchyWithNames(10L)).thenReturn(List.of(city, country));

            LocationResponseDto response = service.findLocationHierarchy(10L);

            assertThat(response.getCity()).isNotNull();
            assertThat(response.getCountry()).isNotNull();
        }

        @Test
        void notFound_throwException() {
            when(locationRepository.findHierarchyWithNames(99L))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.findLocationHierarchy(99L))
                    .isInstanceOf(LocationNotFoundException.class);
        }
    }

    // ==========================================================
    // searchWithPagination
    // ==========================================================
    @Nested
    class SearchWithPaginationTests {

        @Test
        void paginatedResult() {
            LocationEntity entity = new LocationEntity();
            when(locationRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(entity)));

            when(locationMapper.toLocationDto(entity)).thenReturn(new LocationDto());

            PaginationResponse<LocationDto> result =
                    service.searchWithPagination(new PaginationRequest(0, 10));

            assertThat(result.content()).hasSize(1);
        }
    }

    // ==========================================================
    // findLocationWithFallback
    // ==========================================================
    @Nested
    class FallbackTests {

        @Test
        void admin_directLookup() {
            when(locationRepository.findLocationByGeonameIdAndUserId(100L, 1L))
                    .thenReturn(Optional.of(new LocationEntity()));

            Optional<LocationEntity> result = service.findLocationWithFallback(100L, user);

            assertThat(result).isPresent();
        }

        @Test
        void client_fallbackToGlobal() {
            user.setUserType(UserType.CLIENT);

            when(locationRepository.findLocationByGeonameIdAndUserId(any(), any()))
                    .thenReturn(Optional.empty());
            when(locationRepository.findLocationByGeonameIdAndScope(100L, Scope.GLOBAL))
                    .thenReturn(Optional.of(new LocationEntity()));

            Optional<LocationEntity> result = service.findLocationWithFallback(100L, user);

            assertThat(result).isPresent();
        }
    }

    // ==========================================================
    // create (simple path)
    // ==========================================================
    @Nested
    class CreateTests {

        @Test
        void createLocation_withContinentAndCity_success() {

            // --- arrange ---
            LocationDto continent = new LocationDto();
            continent.setGeonameId(1L);

            LocationDto city = new LocationDto();
            city.setGeonameId(123L);

            BaseLocationRequestDto req = new DefaultLocationRequestDto();
            req.setContinent(continent);
            req.setCity(city);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // continent exists
            when(locationRepository.findLocationByGeonameIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(new LocationEntity()));

            // city not found yet
            when(locationRepository.findLocationByGeonameIdAndUserId(123L, 1L))
                    .thenReturn(Optional.empty());

            // dto → entity mapper
            when(locationMapper.dtoToEntity(any(LocationDto.class)))
                    .thenReturn(new LocationEntity());

            // save → return ID = 10
            when(locationRepository.save(any()))
                    .thenAnswer(
                            inv -> {
                                LocationEntity e = inv.getArgument(0);
                                e.setId(10L);
                                return e;
                            });

            // parent lookup
            when(locationRepository.findById(10L)).thenReturn(Optional.of(new LocationEntity()));

            // --- act ---
            Map<LocationLevel, LocationDto> response = service.create(req.getAllLocationMap());

            // --- assert ---
            assertThat(response).isNotNull();
            assertThat(response.get(DefaultLocationLevel.CONTINENT)).isNotNull();
            assertThat(response.get(DefaultLocationLevel.CITY)).isNotNull();
        }
    }

    // ==========================================================
    // buildPathIds
    // ==========================================================
    @Nested
    class BuildPathTests {

        @Test
        void rootPath() {
            String path = service.buildPathIds(null, 10L);
            assertThat(path).isEqualTo("10");
        }

        @Test
        void nestedPath() {
            LocationEntity parent = new LocationEntity();
            parent.setPathIds("1.2");

            String path = service.buildPathIds(parent, 5L);
            assertThat(path).isEqualTo("1.2.5");
        }
    }
}
