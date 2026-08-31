package com.hmg.ipmap.iplocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.iplocation.dto.IpMappingDomainData;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanRepository;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.ipmapping.dto.IpSpanProjection;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpSingle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbIpLocationResolverTest {

    @InjectMocks private DbIpLocationResolver dbIpLocationResolver;

    @Mock private IpSpanRepository ipSpanRepository;
    @Mock private IpNotationFactory ipNotationFactory;
    @Mock private IpMappingService ipMappingService;
    @Mock private IpLocationDomainDataAssembler assembler;

    private static final String TEST_IP = "192.168.1.1";
    private static final long TEST_IP_LONG = 3232235777L;

    @BeforeEach
    void setUp() {
        IpSingle ipSingleMock = mock(IpSingle.class);
        when(ipSingleMock.longValue()).thenReturn(TEST_IP_LONG);
        when(ipNotationFactory.createIpSingle(TEST_IP)).thenReturn(ipSingleMock);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void resolve_AdminUser_ShouldSearchGlobalScopeAndReturnPresent() {
        UserContextHolder.set(
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));

        IpMappingEntity ipMappingEntity = new IpMappingEntity();
        IpSpanProjection projection = mock(IpSpanProjection.class);
        when(projection.getScope()).thenReturn(Scope.GLOBAL);
        when(projection.getIpMappingId()).thenReturn(1L);

        when(ipSpanRepository.findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any()))
                .thenReturn(List.of(projection));
        when(ipMappingService.findByIdWithAttributes(any()))
                .thenReturn(Optional.of(ipMappingEntity));
        when(ipMappingService.getAndSetIpMappingResponseDto(ipMappingEntity))
                .thenReturn(new IpMappingResponseDto());
        IpLocationDomainData domainData = new IpLocationDomainData();
        domainData.setIpMapping(new IpMappingDomainData(TEST_IP, Scope.GLOBAL, null, null, null));
        when(assembler.assemble(any(), eq(TEST_IP), any())).thenReturn(domainData);

        Optional<IpLocationDomainData> result = dbIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isPresent();
        assertThat(result.get().getIpMapping().getIpAddress())
                .isEqualTo(TEST_IP); // set by assembler mock
        verify(ipSpanRepository).findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any());
    }

    @Test
    void resolve_ClientUser_ShouldPreferClientScopeOverGlobal() {
        UserContextHolder.set(
                new UserContext(
                        2L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null));

        IpMappingEntity ipMappingEntity = new IpMappingEntity();

        IpSpanProjection globalProjection = mock(IpSpanProjection.class);
        when(globalProjection.getScope()).thenReturn(Scope.GLOBAL);

        IpSpanProjection clientProjection = mock(IpSpanProjection.class);
        when(clientProjection.getScope()).thenReturn(Scope.CLIENT);
        when(clientProjection.getIpMappingId()).thenReturn(1L);

        when(ipSpanRepository.findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any()))
                .thenReturn(List.of(globalProjection, clientProjection));
        when(ipMappingService.findByIdWithAttributes(any()))
                .thenReturn(Optional.of(ipMappingEntity));
        when(ipMappingService.getAndSetIpMappingResponseDto(ipMappingEntity))
                .thenReturn(new IpMappingResponseDto());
        when(assembler.assemble(any(), eq(TEST_IP), any())).thenReturn(new IpLocationDomainData());

        Optional<IpLocationDomainData> result = dbIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isPresent();
        verify(ipSpanRepository).findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any());
    }

    @Test
    void resolve_ClientUser_ShouldFallbackToGlobalWhenNotFoundInClientScope() {
        UserContextHolder.set(
                new UserContext(
                        2L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null));

        IpMappingEntity ipMappingEntity = new IpMappingEntity();
        IpSpanProjection globalProjection = mock(IpSpanProjection.class);
        when(globalProjection.getScope()).thenReturn(Scope.GLOBAL);
        when(globalProjection.getIpMappingId()).thenReturn(1L);

        when(ipSpanRepository.findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any()))
                .thenReturn(List.of(globalProjection));
        when(ipMappingService.findByIdWithAttributes(any()))
                .thenReturn(Optional.of(ipMappingEntity));
        when(ipMappingService.getAndSetIpMappingResponseDto(ipMappingEntity))
                .thenReturn(new IpMappingResponseDto());
        when(assembler.assemble(any(), eq(TEST_IP), any())).thenReturn(new IpLocationDomainData());

        Optional<IpLocationDomainData> result = dbIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isPresent();
        verify(ipSpanRepository).findAllScopeByIpAndUserId(anyLong(), any());
    }

    @Test
    void resolve_ShouldReturnEmpty_WhenIpNotFoundAnywhere() {
        UserContextHolder.set(
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null));

        when(ipSpanRepository.findAllScopeByIpAndUserId(eq(TEST_IP_LONG), any()))
                .thenReturn(List.of());

        Optional<IpLocationDomainData> result = dbIpLocationResolver.resolve(TEST_IP);

        assertThat(result).isEmpty();
    }
}
