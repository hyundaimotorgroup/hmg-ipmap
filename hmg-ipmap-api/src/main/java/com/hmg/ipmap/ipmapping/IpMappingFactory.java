package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.ipmapping.exception.NotHavePivilegesException;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpNotationType;
import com.hmg.ipmap.ipnotation.IpSpanType;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.ipnotation.exception.IpNotationInvalidException;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationService;
import com.hmg.ipmap.location.LocationServiceImpl;
import com.hmg.ipmap.user.UserEntity;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory responsible for constructing and mutating {@link IpMappingEntity} instances.
 *
 * <p>Consolidates entity-building logic (field population, notation-type resolution, location
 * resolution, and country-id mapping) that previously lived inside {@link IpMappingServiceImpl},
 * keeping the service focused on orchestration rather than construction.
 *
 * <p>Also owns IP-notation validation and notation-type determination so that {@link
 * IpNotationFactory} is a single-point dependency for all notation concerns.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IpMappingFactory {

    private final IpMappingMapper ipMappingMapper;
    private final IpNotationFactory ipNotationFactory;
    private final LocationService locationService;

    /**
     * Validates the given IP notation string.
     *
     * <p>Delegates to {@link IpNotationFactory#validationIpNotation(String)}. If an {@link
     * UnknownHostException} is thrown, it is logged as a warning and execution continues so that
     * callers remain in control of error propagation.
     *
     * @param ipNotation the IP notation to validate
     */
    public void validateIpNotation(String ipNotation) {
        try {
            ipNotationFactory.validationIpNotation(ipNotation);
        } catch (UnknownHostException e) {
            log.warn(
                    "UnknownHostException when validating IpNotation {} {}",
                    ipNotation,
                    e.getMessage());
        }
    }

    /**
     * Determines the {@link NotationType} for the given IP notation string.
     *
     * @param ipNotation the IP notation to classify
     * @return the resolved {@link NotationType}
     * @throws IpNotationInvalidException if the notation cannot be classified
     */
    public NotationType determineNotationType(String ipNotation) {
        IpNotationType ipNotationType =
                ipNotationFactory
                        .determineIpNotationType(ipNotation)
                        .orElseThrow(() -> new IpNotationInvalidException(ipNotation));

        return switch (ipNotationType) {
            case IP_ARRAY -> NotationType.ARRAY;
            case IP_SINGLE -> NotationType.SINGLE;
            case IP_SPAN -> {
                IpSpanType ipSpanType =
                        ipNotationFactory
                                .determineIpSpanType(ipNotation)
                                .orElseThrow(() -> new IpNotationInvalidException(ipNotation));
                yield switch (ipSpanType) {
                    case CIDR -> NotationType.CIDR;
                    case RANGE -> NotationType.RANGE;
                    case WILDCARD -> NotationType.WILDCARD;
                };
            }
        };
    }

    /**
     * Builds a new {@link IpMappingEntity} from the given request, user, scope, and location
     * geonameId.
     *
     * <p>Resolves the {@link LocationEntity} via {@link
     * LocationServiceImpl#findLocationWithFallback(Long, UserEntity)}, which enforces ownership and
     * prevents IDOR by design.
     *
     * @param request the create request DTO
     * @param user the authenticated user who will own the mapping
     * @param scope the scope to assign to the entity
     * @param geonameId the geonameId of the most-specific location in the request
     * @return a new, unsaved {@link IpMappingEntity}
     * @throws NotFoundException if no location can be resolved for {@code geonameId}
     * @throws NotHavePivilegesException if the user is not allowed to reference the location
     */
    public IpMappingEntity buildForCreate(
            IpMappingRequestDto request, UserEntity user, Scope scope, Long geonameId) {
        IpMappingEntity entity = ipMappingMapper.toEntity(request);
        entity.setUser(user);
        entity.setScope(scope);
        entity.setNotationType(determineNotationType(entity.getIpNotation()));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(null);
        entity.setLocation(resolveLocation(geonameId, user));
        applyCountryIds(entity, request);
        return entity;
    }

    /**
     * Applies updated fields from {@code request} to the existing {@code entity}.
     *
     * <p>Resolves the location using {@code currentUser} rather than the entity's existing owner so
     * that scope and ownership rules are evaluated against the authenticated requester (e.g. an
     * admin acting on another user's mapping).
     *
     * @param entity the existing entity to mutate
     * @param request the update request DTO
     * @param geonameId the geonameId of the most-specific location in the request
     * @param currentUser the authenticated user performing the update
     * @throws NotFoundException if no location can be resolved for {@code geonameId}
     */
    public void applyUpdates(
            IpMappingEntity entity,
            IpMappingRequestDto request,
            Long geonameId,
            UserEntity currentUser) {
        entity.setUpdatedAt(Instant.now());
        entity.setValidPeriod(request.validPeriod());
        entity.setIpNotation(request.ipNotation());
        entity.setNotationType(determineNotationType(request.ipNotation()));
        entity.setLocation(resolveLocation(geonameId, currentUser));
        applyCountryIds(entity, request);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private LocationEntity resolveLocation(Long geonameId, UserEntity user) {
        return locationService
                .findLocationWithFallback(geonameId, user)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Location not found with geonameId: " + geonameId));
    }

    private void applyCountryIds(IpMappingEntity entity, IpMappingRequestDto request) {
        Optional.ofNullable(request.representedCountryGeonameId())
                .ifPresent(entity::setRepresentedCountryGeonameId);
        Optional.ofNullable(request.registeredCountryGeonameId())
                .ifPresent(entity::setRegisteredCountryGeonameId);
    }
}
