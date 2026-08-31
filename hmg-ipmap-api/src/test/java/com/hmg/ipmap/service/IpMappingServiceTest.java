package com.hmg.ipmap.service;

import static com.hmg.ipmap.ipmapping.IpMappingServiceImpl.IP_MAPPING_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.ipmapping.IpMappingAttributeMapper;
import com.hmg.ipmap.ipmapping.IpMappingAttributeService;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingFactory;
import com.hmg.ipmap.ipmapping.IpMappingMapper;
import com.hmg.ipmap.ipmapping.IpMappingResponseBuilder;
import com.hmg.ipmap.ipmapping.IpMappingServiceImpl;
import com.hmg.ipmap.ipmapping.IpSpanService;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationServiceImpl;
import com.hmg.ipmap.location.dto.DefaultLocationRequestDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class IpMappingServiceTest {

    @Mock private IpMappingRepository ipMappingRepository;
    @Mock private IpMappingMapper ipMappingMapper;
    @Mock private LocationServiceImpl locationService;
    @Mock private UserService userService;

    @Mock
    @SuppressWarnings("unused")
    private IpMappingAttributeMapper attributeMapper;

    @Mock private IpMappingResponseBuilder responseBuilder;
    @Mock private IpSpanService ipSpanService;
    @Mock private IpSpanProperties ipSpanProperties;
    @Mock private IpMappingFactory ipMappingFactory;
    @Mock private IpMappingAttributeService ipMappingAttributeService;
    @Mock private LocationRepository locationRepository;
    @Mock private LocationNameRepository locationNameRepository;

    @InjectMocks private IpMappingServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void baseSetup() {
        user = new UserEntity();
        user.setId(1L);
        UserContextHolder.set(
                new UserContext(1L, "user", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    // ========================================================================
    // searchWithPagination
    // ========================================================================
    @Nested
    class SearchWithPaginationTests {

        @Test
        void adminScope_usesGlobalScope() {
            IpMappingEntity entity = new IpMappingEntity();
            entity.setId(1L);
            Page<IpMappingEntity> page = new PageImpl<>(List.of(entity));

            when(ipMappingRepository.findAllByScope(eq(Scope.GLOBAL), any())).thenReturn(page);
            when(ipMappingAttributeService.fetchByIpMappingIds(any())).thenReturn(List.of());
            when(locationRepository.findLocationRecursiveBatch(any())).thenReturn(List.of());
            when(locationNameRepository.findAllByLocationIdIn(any())).thenReturn(List.of());
            when(responseBuilder.buildIpMappingResponseDtoList(any(), any(), any(), any()))
                    .thenReturn(List.of(new IpMappingResponseDto()));

            PaginationResponse<IpMappingResponseDto> result =
                    service.searchWithPagination(new PaginationRequest(0, 10));

            assertThat(result.content()).hasSize(1);
        }
    }

    // ========================================================================
    // searchById
    // ========================================================================
    @Nested
    class SearchByIdTests {

        @Test
        void success() {
            IpMappingEntity entity = new IpMappingEntity();
            entity.setId(10L);
            entity.setUser(user);

            when(ipMappingRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(ipMappingAttributeService.fetchByIpMappingIds(any())).thenReturn(List.of());
            when(locationRepository.findLocationRecursiveBatch(any())).thenReturn(List.of());
            when(locationNameRepository.findAllByLocationIdIn(any())).thenReturn(List.of());
            when(responseBuilder.buildIpMappingResponseDtoList(any(), any(), any(), any()))
                    .thenReturn(List.of(new IpMappingResponseDto()));

            IpMappingResponseDto dto = service.searchById(10L);

            assertThat(dto).isNotNull();
        }

        @Test
        void mappingNotFound_throwsNotFoundException() {
            assertThatThrownBy(() -> service.searchById(10L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(IP_MAPPING_NOT_FOUND);
        }
    }

    // ========================================================================
    // create
    // ========================================================================
    @Nested
    class CreateTests {

        @Test
        void success_singleIp() {
            IpMappingRequestDto req = mock(IpMappingRequestDto.class);

            DefaultLocationRequestDto loc = new DefaultLocationRequestDto();
            LocationDto city = new LocationDto();
            city.setGeonameId(123L);
            loc.setCity(city);

            when(req.location()).thenReturn(loc);
            when(req.ipNotation()).thenReturn("1.1.1.1");
            when(req.representedCountryGeonameId()).thenReturn(null);
            when(req.registeredCountryGeonameId()).thenReturn(null);

            when(userService.getEntityById(1L)).thenReturn(user);

            when(locationService.createLocation(any(UserEntity.class), any(Map.class), eq(false)))
                    .thenReturn(Map.of(DefaultLocationLevel.CITY, city));

            IpMappingEntity entity = new IpMappingEntity();
            when(ipMappingFactory.buildForCreate(any(), any(), any(), any())).thenReturn(entity);

            when(ipMappingRepository.save(any()))
                    .thenAnswer(
                            i -> {
                                IpMappingEntity e = i.getArgument(0);
                                e.setId(10L);
                                return e;
                            });

            when(ipMappingMapper.reqDtoToRespDto(req)).thenReturn(new IpMappingResponseDto());

            IpMappingResponseDto result = service.create(req);

            assertThat(result).isNotNull();
            verify(ipMappingFactory).validateIpNotation("1.1.1.1");
            verify(ipMappingFactory).buildForCreate(any(), eq(user), eq(Scope.GLOBAL), any());
            verify(ipMappingAttributeService).replaceAttributes(eq(req), any());
            verify(ipSpanService).updateIpSpans(any());
        }
    }

    // ========================================================================
    // update
    // ========================================================================
    @Nested
    class UpdateTests {

        @Test
        void success() {
            IpMappingRequestDto req = mock(IpMappingRequestDto.class);
            DefaultLocationRequestDto loc = new DefaultLocationRequestDto();
            LocationDto city = new LocationDto();
            city.setGeonameId(99L);
            loc.setCity(city);

            when(req.location()).thenReturn(loc);
            when(req.ipNotation()).thenReturn("1.1.1.1");
            when(req.representedCountryGeonameId()).thenReturn(null);
            when(req.registeredCountryGeonameId()).thenReturn(null);

            IpMappingEntity entity = new IpMappingEntity();
            entity.setUser(user);

            when(userService.getEntityById(1L)).thenReturn(user);
            when(ipMappingRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(locationService.createLocation(any(), any(Map.class), eq(false)))
                    .thenReturn(Map.of());

            when(ipMappingRepository.save(entity)).thenReturn(entity);
            when(ipMappingMapper.reqDtoToRespDto(req)).thenReturn(new IpMappingResponseDto());

            IpMappingResponseDto dto = service.update(10L, req);
            assertThat(dto).isNotNull();
            verify(ipMappingFactory).validateIpNotation("1.1.1.1");
            verify(ipMappingFactory).applyUpdates(eq(entity), eq(req), any(), eq(user));
        }
    }

    // ========================================================================
    // delete
    // ========================================================================
    @Nested
    class DeleteTests {

        @Test
        void success() {
            IpMappingEntity entity = new IpMappingEntity();
            entity.setUser(user);

            when(ipMappingRepository.findById(10L)).thenReturn(Optional.of(entity));

            service.delete(10L);

            verify(ipSpanService).deleteAllByIpMapping(entity);
            verify(ipMappingAttributeService).deleteAllByIpMapping(entity);
            verify(ipMappingRepository).delete(entity);
        }
    }

    // ========================================================================
    // rebuildAllIpSpans
    // ========================================================================
    @Nested
    class RebuildAllIpSpansTests {

        @BeforeEach
        void setupProps() {
            IpSpanProperties.Rebuild rebuild = mock(IpSpanProperties.Rebuild.class);
            when(ipSpanProperties.getRebuild()).thenReturn(rebuild);
            when(rebuild.getChunkSize()).thenReturn(2);
            when(ipSpanProperties.getSubnetPrefixLength()).thenReturn(32);
        }

        @Test
        void multipleChunks() {
            when(ipMappingRepository.findWithUserInRange(anyLong(), anyLong(), any()))
                    .thenReturn(List.of(entity(1), entity(2)))
                    .thenReturn(List.of(entity(3)))
                    .thenReturn(List.of());

            service.rebuildAllIpSpans(1, 10);

            verify(ipSpanService, times(2)).rebuildIpSpans(anyList());
        }
    }

    private IpMappingEntity entity(long id) {
        IpMappingEntity e = new IpMappingEntity();
        e.setId(id);
        return e;
    }
}
