package com.hmg.ipmap.ipmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpNotationType;
import com.hmg.ipmap.ipnotation.IpSpanType;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.ipnotation.exception.IpNotationInvalidException;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationService;
import com.hmg.ipmap.location.dto.DefaultLocationRequestDto;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.user.UserEntity;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IpMappingFactoryTest {

    @Mock private IpMappingMapper ipMappingMapper;
    @Mock private IpNotationFactory ipNotationFactory;
    @Mock private LocationService locationService;

    @InjectMocks private IpMappingFactory factory;

    // =========================================================================
    // validateIpNotation
    // =========================================================================
    @Nested
    class ValidateIpNotationTests {

        @Test
        void validNotation_doesNotThrow() throws UnknownHostException {
            factory.validateIpNotation("1.2.3.4");
            verify(ipNotationFactory).validationIpNotation("1.2.3.4");
        }

        @Test
        void unknownHostException_isSwallowedWithWarning() throws UnknownHostException {
            doThrow(new UnknownHostException("bad"))
                    .when(ipNotationFactory)
                    .validationIpNotation(anyString());

            // should not throw
            factory.validateIpNotation("bad-ip");
        }
    }

    // =========================================================================
    // determineNotationType
    // =========================================================================
    @Nested
    class DetermineNotationTypeTests {

        @Test
        void ipArray_returnsARRAY() {
            when(ipNotationFactory.determineIpNotationType("1.1.1.1,2.2.2.2"))
                    .thenReturn(Optional.of(IpNotationType.IP_ARRAY));

            assertThat(factory.determineNotationType("1.1.1.1,2.2.2.2"))
                    .isEqualTo(NotationType.ARRAY);
        }

        @Test
        void ipSingle_returnsSINGLE() {
            when(ipNotationFactory.determineIpNotationType("1.2.3.4"))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));

            assertThat(factory.determineNotationType("1.2.3.4")).isEqualTo(NotationType.SINGLE);
        }

        @ParameterizedTest
        @EnumSource(value = IpSpanType.class)
        void ipSpan_mapsToCorrectNotationType(IpSpanType spanType) {
            when(ipNotationFactory.determineIpNotationType(anyString()))
                    .thenReturn(Optional.of(IpNotationType.IP_SPAN));
            when(ipNotationFactory.determineIpSpanType(anyString()))
                    .thenReturn(Optional.of(spanType));

            NotationType result = factory.determineNotationType("10.0.0.0/24");

            NotationType expected =
                    switch (spanType) {
                        case CIDR -> NotationType.CIDR;
                        case RANGE -> NotationType.RANGE;
                        case WILDCARD -> NotationType.WILDCARD;
                    };
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void unresolvableNotationType_throwsIpNotationInvalidException() {
            when(ipNotationFactory.determineIpNotationType(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> factory.determineNotationType("??"))
                    .isInstanceOf(IpNotationInvalidException.class);
        }

        @Test
        void unresolvableSpanType_throwsIpNotationInvalidException() {
            when(ipNotationFactory.determineIpNotationType(anyString()))
                    .thenReturn(Optional.of(IpNotationType.IP_SPAN));
            when(ipNotationFactory.determineIpSpanType(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> factory.determineNotationType("10.0.0.0/24"))
                    .isInstanceOf(IpNotationInvalidException.class);
        }
    }

    // =========================================================================
    // buildForCreate
    // =========================================================================
    @Nested
    class BuildForCreateTests {

        @Test
        void populatesAllRequiredFields() {
            UserEntity user = new UserEntity();
            user.setId(1L);

            DefaultLocationRequestDto locationDto = new DefaultLocationRequestDto();
            LocationDto country = new LocationDto();
            country.setGeonameId(100L);
            locationDto.setCountry(country);

            IpMappingRequestDto request =
                    new IpMappingRequestDto(
                            "1.2.3.4",
                            Instant.now().plusSeconds(3600),
                            null,
                            locationDto,
                            null,
                            null);

            IpMappingEntity blankEntity = new IpMappingEntity();
            blankEntity.setIpNotation("1.2.3.4");
            when(ipMappingMapper.toEntity(request)).thenReturn(blankEntity);
            when(ipNotationFactory.determineIpNotationType("1.2.3.4"))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));

            LocationEntity location = new LocationEntity();
            when(locationService.findLocationWithFallback(100L, user))
                    .thenReturn(Optional.of(location));

            IpMappingEntity result = factory.buildForCreate(request, user, Scope.GLOBAL, 100L);

            assertThat(result.getUser()).isEqualTo(user);
            assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
            assertThat(result.getNotationType()).isEqualTo(NotationType.SINGLE);
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNull();
            assertThat(result.getLocation()).isEqualTo(location);
        }

        @Test
        void locationNotFound_throwsNotFoundException() {
            UserEntity user = new UserEntity();
            IpMappingRequestDto request =
                    new IpMappingRequestDto(
                            "1.2.3.4", null, null, new DefaultLocationRequestDto(), null, null);

            IpMappingEntity blankEntity = new IpMappingEntity();
            blankEntity.setIpNotation("1.2.3.4");
            when(ipMappingMapper.toEntity(request)).thenReturn(blankEntity);
            when(ipNotationFactory.determineIpNotationType(any()))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));
            when(locationService.findLocationWithFallback(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> factory.buildForCreate(request, user, Scope.GLOBAL, 999L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void registeredAndRepresentedCountryIds_areApplied() {
            UserEntity user = new UserEntity();

            LocationDto country = new LocationDto();
            country.setGeonameId(200L);

            DefaultLocationRequestDto locationDto = new DefaultLocationRequestDto();
            locationDto.setCountry(country);

            IpMappingRequestDto request =
                    new IpMappingRequestDto("1.2.3.4", null, null, locationDto, 202L, 201L);

            IpMappingEntity blankEntity = new IpMappingEntity();
            blankEntity.setIpNotation("1.2.3.4");
            when(ipMappingMapper.toEntity(request)).thenReturn(blankEntity);
            when(ipNotationFactory.determineIpNotationType(any()))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));
            when(locationService.findLocationWithFallback(any(), any()))
                    .thenReturn(Optional.of(new LocationEntity()));

            IpMappingEntity result = factory.buildForCreate(request, user, Scope.CLIENT, 200L);

            assertThat(result.getRegisteredCountryGeonameId()).isEqualTo(201L);
            assertThat(result.getRepresentedCountryGeonameId()).isEqualTo(202L);
        }
    }

    // =========================================================================
    // applyUpdates
    // =========================================================================
    @Nested
    class ApplyUpdatesTests {

        @Test
        void updatesAllMutableFields() {
            UserEntity currentUser = new UserEntity();
            currentUser.setId(42L);

            IpMappingEntity entity = new IpMappingEntity();
            Instant newValidPeriod = Instant.now().plusSeconds(7200);
            DefaultLocationRequestDto locationDto = new DefaultLocationRequestDto();
            IpMappingRequestDto request =
                    new IpMappingRequestDto(
                            "10.0.0.1", newValidPeriod, null, locationDto, null, null);

            when(ipNotationFactory.determineIpNotationType("10.0.0.1"))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));
            LocationEntity newLocation = new LocationEntity();
            when(locationService.findLocationWithFallback(null, currentUser))
                    .thenReturn(Optional.of(newLocation));

            factory.applyUpdates(entity, request, null, currentUser);

            assertThat(entity.getUpdatedAt()).isNotNull();
            assertThat(entity.getValidPeriod()).isEqualTo(newValidPeriod);
            assertThat(entity.getIpNotation()).isEqualTo("10.0.0.1");
            assertThat(entity.getNotationType()).isEqualTo(NotationType.SINGLE);
            assertThat(entity.getLocation()).isEqualTo(newLocation);
        }

        @Test
        void locationResolutionUsesCurrentUser_notEntityOwner() {
            UserEntity entityOwner = new UserEntity();
            entityOwner.setId(1L);
            UserEntity currentUser = new UserEntity();
            currentUser.setId(99L);

            IpMappingEntity entity = new IpMappingEntity();
            entity.setUser(entityOwner);

            IpMappingRequestDto request =
                    new IpMappingRequestDto(
                            "1.2.3.4", null, null, new DefaultLocationRequestDto(), null, null);

            when(ipNotationFactory.determineIpNotationType(any()))
                    .thenReturn(Optional.of(IpNotationType.IP_SINGLE));
            when(locationService.findLocationWithFallback(any(), eq(currentUser)))
                    .thenReturn(Optional.of(new LocationEntity()));

            factory.applyUpdates(entity, request, 50L, currentUser);

            // should use currentUser (id=99), not entityOwner (id=1)
            verify(locationService).findLocationWithFallback(50L, currentUser);
        }
    }
}
