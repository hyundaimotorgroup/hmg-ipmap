package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.ipmapping.dto.IpMappingLocationDto;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.dto.LocationDto;
import com.hmg.ipmap.location.dto.LocationResponseDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pure DTO assembler that converts pre-fetched {@link IpMappingEntity} data into {@link
 * IpMappingResponseDto} instances.
 *
 * <p>This component contains <em>no repository dependencies</em>. All required data (attributes,
 * location hierarchy, location names) must be fetched by the caller and supplied as parameters.
 * This makes the assembler straightforward to unit-test without database mocks.
 *
 * <p>The primary entry points are:
 *
 * <ul>
 *   <li>{@link #buildIpMappingResponseDtoList} — batch assembly from pre-fetched collections
 *   <li>{@link #buildIpMappingResponseDto} — single assembly from a pre-loaded location hierarchy
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpMappingResponseBuilder {

    private final IpMappingMapper ipMappingMapper;

    /**
     * Assembles response DTOs from pre-fetched entity and location data.
     *
     * <p>All related data must already be loaded; this method performs no additional queries.
     *
     * @param ipMappingEntityList the IP mapping entities to convert
     * @param ipMappingAttributeEntityList pre-fetched attribute entities for the mappings
     * @param locationEntityList pre-fetched location entities (including ancestor hierarchy)
     * @param locationNameEntityList pre-fetched location name entities
     * @return list of populated response DTOs, or an empty list if the input is null or empty
     */
    public List<IpMappingResponseDto> buildIpMappingResponseDtoList(
            List<IpMappingEntity> ipMappingEntityList,
            List<IpMappingAttributeEntity> ipMappingAttributeEntityList,
            List<LocationEntity> locationEntityList,
            List<LocationNameEntity> locationNameEntityList) {
        if (ipMappingEntityList == null || ipMappingEntityList.isEmpty()) {
            return List.of();
        }

        Map<Long, List<IpMappingAttributeEntity>> attributesByIpMappingId =
                groupAttributesByIpMappingId(ipMappingAttributeEntityList);
        Map<Long, LocationEntity> locationsById = groupLocationsById(locationEntityList);
        Map<Long, Map<String, String>> namesByLocationId =
                groupNamesByLocationId(locationNameEntityList);

        return ipMappingEntityList.stream()
                .map(
                        ipMapping ->
                                buildIpMappingResponseDto(
                                        ipMapping,
                                        attributesByIpMappingId,
                                        locationsById,
                                        namesByLocationId))
                .toList();
    }

    /**
     * Builds a response DTO from a pre-fetched location hierarchy and attribute list.
     *
     * @param ipMappingEntity the IP mapping entity
     * @param locationResponseDto the resolved location hierarchy
     * @param ipMappingAttributeEntities the attribute entities to apply to the response location
     * @return the populated response DTO
     */
    public IpMappingResponseDto buildIpMappingResponseDto(
            IpMappingEntity ipMappingEntity,
            LocationResponseDto locationResponseDto,
            List<IpMappingAttributeEntity> ipMappingAttributeEntities,
            LocationDto registeredCountry,
            LocationDto representedCountry) {
        log.trace("Set response ip mapping IpMappingLocationDto locationResponse");
        IpMappingLocationDto locationResponse = convertToIpMappingLocationDto(locationResponseDto);

        IpMappingResponseDto response = ipMappingMapper.toDto(ipMappingEntity);
        response.setRepresentedCountry(representedCountry);
        response.setRegisteredCountry(registeredCountry);
        response.setValidPeriod(ipMappingEntity.getValidPeriod());
        response.setLocation(locationResponse);
        applyAttributes(response, ipMappingAttributeEntities);

        return response;
    }

    /**
     * Applies a list of attribute entities to the given response DTO, populating its {@code
     * attributes} map keyed by each entity's {@code objectName}.
     *
     * @param response the response DTO to mutate
     * @param attrs the attribute entities to apply; ignored if {@code null} or empty
     */
    private void applyAttributes(
            IpMappingResponseDto response, List<IpMappingAttributeEntity> attrs) {
        if (attrs == null || attrs.isEmpty()) return;

        Map<String, Map<String, Object>> attributesMap = new HashMap<>();
        for (IpMappingAttributeEntity a : attrs) {
            if (a.getObjectName() == null || a.getAttributes() == null) continue;
            attributesMap.put(a.getObjectName(), a.getAttributes());
        }
        if (!attributesMap.isEmpty()) {
            response.setAttributes(attributesMap);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private IpMappingResponseDto buildIpMappingResponseDto(
            IpMappingEntity ipMapping,
            Map<Long, List<IpMappingAttributeEntity>> attributesByIpMappingId,
            Map<Long, LocationEntity> locationsById,
            Map<Long, Map<String, String>> namesByLocationId) {

        IpMappingResponseDto response = new IpMappingResponseDto();
        response.setId(ipMapping.getId());
        response.setValidPeriod(ipMapping.getValidPeriod());
        response.setIpNotation(ipMapping.getIpNotation());
        response.setScope(ipMapping.getScope());

        IpMappingLocationDto locationDto =
                buildIpMappingLocationDto(
                        ipMapping.getLocation(), locationsById, namesByLocationId);

        response.setLocation(locationDto);
        applyAttributes(
                response, attributesByIpMappingId.getOrDefault(ipMapping.getId(), List.of()));

        return response;
    }

    private IpMappingLocationDto buildIpMappingLocationDto(
            LocationEntity child,
            Map<Long, LocationEntity> locationById,
            Map<Long, Map<String, String>> namesByLocationId) {
        IpMappingLocationDto ipMappingLocationDto = new IpMappingLocationDto();
        if (child == null) {
            return ipMappingLocationDto;
        }

        LocationEntity current = child;
        Set<Long> visited = new HashSet<>();
        List<LocationDto> subdivisions = new ArrayList<>();

        while (current != null && visited.add(current.getId())) {
            LocationEntity node = locationById.getOrDefault(current.getId(), current);
            LocationDto nodeDto =
                    toLocationDto(node, namesByLocationId.getOrDefault(node.getId(), Map.of()));

            String level = node.getLocationLevel();
            if (level == null) {
                current = node.getParent();
                continue;
            }

            switch (level) {
                case "CONTINENT" -> ipMappingLocationDto.setContinent(nodeDto);
                case "COUNTRY" -> ipMappingLocationDto.setCountry(nodeDto);
                case "CITY" -> ipMappingLocationDto.setCity(nodeDto);
                case "REGION", "SUBDIVISION1", "SUBDIVISION2" -> subdivisions.add(nodeDto);
                default -> {
                    /**/
                }
            }

            current = node.getParent();
        }

        if (!subdivisions.isEmpty()) {
            ipMappingLocationDto.setAdditionalLocations(subdivisions);
        }

        return ipMappingLocationDto;
    }

    private LocationDto toLocationDto(LocationEntity e, Map<String, String> names) {
        LocationDto d = new LocationDto();
        d.setLocationCode(e.getLocationCode());
        d.setGeonameId(e.getGeonameId());
        d.setAttributes(e.getAttributes());
        d.setId(e.getId());
        if (!names.isEmpty()) {
            d.setNames(names);
        }
        return d;
    }

    private IpMappingLocationDto convertToIpMappingLocationDto(
            LocationResponseDto locationResponseDto) {
        IpMappingLocationDto ipMappingLocationDto = new IpMappingLocationDto();
        ipMappingLocationDto.setContinent(locationResponseDto.getContinent());
        ipMappingLocationDto.setCountry(locationResponseDto.getCountry());
        ipMappingLocationDto.setCity(locationResponseDto.getCity());
        ipMappingLocationDto.setAdditionalLocations(locationResponseDto.getAdditionalLocations());
        return ipMappingLocationDto;
    }

    private Map<Long, List<IpMappingAttributeEntity>> groupAttributesByIpMappingId(
            List<IpMappingAttributeEntity> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return attributes.stream()
                .collect(Collectors.groupingBy(attribute -> attribute.getIpMapping().getId()));
    }

    private Map<Long, LocationEntity> groupLocationsById(List<LocationEntity> locations) {
        if (locations == null || locations.isEmpty()) {
            return Map.of();
        }
        return locations.stream()
                .collect(
                        Collectors.toMap(
                                LocationEntity::getId,
                                Function.identity(),
                                (existing, replacement) -> existing));
    }

    private Map<Long, Map<String, String>> groupNamesByLocationId(
            List<LocationNameEntity> locationNames) {
        if (locationNames == null || locationNames.isEmpty()) {
            return Map.of();
        }
        return locationNames.stream()
                .collect(
                        Collectors.groupingBy(
                                name -> name.getLocation().getId(),
                                Collectors.toMap(
                                        name -> name.getLocationNameId().getLocaleCode(),
                                        LocationNameEntity::getName,
                                        (existing, replacement) -> replacement)));
    }
}
