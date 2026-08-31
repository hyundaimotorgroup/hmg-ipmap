package com.hmg.ipmap.iplocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.CacheService;
import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingMapper;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanEntity;
import com.hmg.ipmap.ipmapping.IpSpanMapper;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationMapper;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameMapper;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheIpLocationResolverTest {

    @InjectMocks private CacheIpLocationResolver cacheIpLocationResolver;

    @Mock private CacheService cacheService;
    @Mock private IpSpanMapper ipSpanMapper;
    @Mock private IpMappingMapper ipMappingMapper;
    @Mock private LocationMapper locationMapper;
    @Mock private LocationNameMapper locationNameMapper;
    @Mock private IpMappingService ipMappingService;
    @Mock private IpLocationDomainDataAssembler assembler;

    private static final String TEST_IP = "192.168.1.1";
    private UserContext adminCtx;

    @BeforeEach
    void setUp() {
        adminCtx =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(adminCtx);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void resolve_CacheHit_WithIpMappingDto_ShouldReturnPresent() {
        IpSpanCacheDto cacheDto = new IpSpanCacheDto();
        IpMappingCacheDto ipMappingCacheDto = new IpMappingCacheDto();
        LocationCacheDto locationCacheDto = new LocationCacheDto();
        locationCacheDto.setNameDtos(new ArrayList<>());
        ipMappingCacheDto.setLocationDto(locationCacheDto);
        cacheDto.setIpMappingDto(ipMappingCacheDto);

        IpSpanEntity ipSpanEntity = new IpSpanEntity();
        IpMappingEntity ipMappingEntity = new IpMappingEntity();
        ipMappingEntity.setScope(Scope.GLOBAL);
        ipSpanEntity.setIpMapping(ipMappingEntity);

        when(cacheService.findIpLocation(TEST_IP, adminCtx)).thenReturn(cacheDto);
        when(ipSpanMapper.cacheToEntity(any())).thenReturn(ipSpanEntity);
        when(ipMappingMapper.cacheToEntity(any())).thenReturn(ipMappingEntity);
        lenient().when(locationMapper.cacheToEntity(any())).thenReturn(new LocationEntity());
        lenient()
                .when(locationNameMapper.cacheToEntity(any()))
                .thenReturn(new LocationNameEntity());
        when(ipMappingService.getAndSetIpMappingResponseDto(any(), any()))
                .thenReturn(new IpMappingResponseDto());
        when(assembler.assemble(any(), eq(TEST_IP), any())).thenReturn(new IpLocationDomainData());

        Optional<IpLocationDomainData> result = cacheIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isPresent();
        verify(cacheService).findIpLocation(TEST_IP, adminCtx);
    }

    @Test
    void resolve_CacheMiss_ShouldReturnEmpty() {
        when(cacheService.findIpLocation(any(), any())).thenReturn(null);

        Optional<IpLocationDomainData> result = cacheIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_CacheException_ShouldReturnEmpty() {
        when(cacheService.findIpLocation(any(), any()))
                .thenThrow(new RuntimeException("Cache connection error"));

        Optional<IpLocationDomainData> result = cacheIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isEmpty();
    }
}
