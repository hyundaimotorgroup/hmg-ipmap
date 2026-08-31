package com.hmg.ipmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.iplocation.IpLocationResolver;
import com.hmg.ipmap.iplocation.IpLocationResult;
import com.hmg.ipmap.iplocation.IpLocationServiceImpl;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.iplocation.dto.IpMappingDomainData;
import com.hmg.ipmap.iplocation.response.TemplateResponseStrategyFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class IpLocationServiceTest {

    @Mock private IpLocationResolver cacheResolver;
    @Mock private IpLocationResolver dbResolver;
    @Mock private TemplateResponseStrategyFactory strategyFactory;

    private IpLocationServiceImpl ipLocationService;

    private static final String TEST_IP = "192.168.1.1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ipLocationService =
                new IpLocationServiceImpl(List.of(cacheResolver, dbResolver), strategyFactory);
        UserContextHolder.set(
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));
        when(strategyFactory.get(any())).thenReturn(OBJECT_MAPPER::writeValueAsString);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void findLocationByIpAddress_CacheResolverSucceeds_ShouldReturnResult() {
        IpLocationDomainData domainData = new IpLocationDomainData();
        domainData.setIpMapping(new IpMappingDomainData(TEST_IP, Scope.GLOBAL, null, null, null));
        when(cacheResolver.resolve(TEST_IP)).thenReturn(Optional.of(domainData));

        IpLocationResult result = ipLocationService.findLocationByIpAddress(TEST_IP);

        assertThat(result.body()).isNotNull();
        assertThat(result.notFound()).isFalse();
        assertThat(result.scope()).contains(Scope.GLOBAL);
        verify(cacheResolver).resolve(TEST_IP);
    }

    @Test
    void findLocationByIpAddress_CacheMiss_ShouldFallbackToDbResolver() {
        IpLocationDomainData domainData = new IpLocationDomainData();
        domainData.setIpMapping(new IpMappingDomainData(TEST_IP, Scope.GLOBAL, null, null, null));
        when(cacheResolver.resolve(TEST_IP)).thenReturn(Optional.empty());
        when(dbResolver.resolve(TEST_IP)).thenReturn(Optional.of(domainData));

        IpLocationResult result = ipLocationService.findLocationByIpAddress(TEST_IP);

        assertThat(result.body()).isNotNull();
        assertThat(result.notFound()).isFalse();
        assertThat(result.scope()).contains(Scope.GLOBAL);
        verify(cacheResolver).resolve(TEST_IP);
        verify(dbResolver).resolve(TEST_IP);
    }

    @Test
    void findLocationByIpAddress_AllResolversEmpty_ShouldReturnNotFoundMarker() {
        when(cacheResolver.resolve(TEST_IP)).thenReturn(Optional.empty());
        when(dbResolver.resolve(TEST_IP)).thenReturn(Optional.empty());

        IpLocationResult result = ipLocationService.findLocationByIpAddress(TEST_IP);

        assertThat(result.notFound()).isTrue();
        assertThat(result.body()).contains("__NOT_FOUND__");
        assertThat(result.scope()).isEmpty();
    }
}
