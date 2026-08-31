package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.iplocation.exception.IpSpanNotFoundException;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanRepository;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import com.hmg.ipmap.ipmapping.dto.IpSpanProjection;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Order(2)
@RequiredArgsConstructor
public class DbIpLocationResolver implements IpLocationResolver {

    private final IpSpanRepository ipSpanRepository;
    private final IpNotationFactory ipNotationFactory;
    private final IpMappingService ipMappingService;
    private final IpLocationDomainDataAssembler assembler;

    @Value("${constant.admin.id}")
    private Long adminId;

    @Transactional(readOnly = true)
    @Override
    public Optional<IpLocationDomainData> resolve(String ip) {
        try {
            IpSpanProjection ipSpanProjection = findIpSpanByIp(ip);
            IpMappingEntity ipMapping =
                    ipMappingService
                            .findByIdWithAttributes(ipSpanProjection.getIpMappingId())
                            .orElseThrow(() -> new NotFoundException("Ip Mapping not found"));
            IpMappingResponseDto dto = ipMappingService.getAndSetIpMappingResponseDto(ipMapping);

            return Optional.of(assembler.assemble(dto, ip, dto.getScope()));
        } catch (IpSpanNotFoundException _) {
            log.warn("Ip address location is not found. ip={}", ip);
            return Optional.empty();
        }
    }

    /**
     * Finds an IP span for the given IP address within the requester's accessible scope. Searches
     * through the user hierarchy from the requester up to global scope.
     *
     * @param ip the IP address to search for
     * @return the IP span projection
     * @throws IpSpanNotFoundException if no IP span is found in any accessible scope
     */
    private IpSpanProjection findIpSpanByIp(String ip) {
        long ipAsLong = ipNotationFactory.createIpSingle(ip).longValue();
        UserContext user = UserContextHolder.get();
        return findIpSpanInUserHierarchy(ipAsLong, user)
                .orElseThrow(() -> new IpSpanNotFoundException("Location not found for IP: " + ip));
    }

    /**
     * Iteratively searches for an IP span through the user hierarchy with cycle detection. Search
     * order: current user scope -> parent scope -> ... -> global scope
     *
     * @param ipAsLong the IP address as a long value
     * @param user the current user in the hierarchy
     * @return Optional containing the IP span if found, empty otherwise
     */
    private Optional<IpSpanProjection> findIpSpanInUserHierarchy(long ipAsLong, UserContext user) {
        Stream<Long> hierarchyIds =
                Stream.of(user, user.parent()).filter(Objects::nonNull).map(UserContext::id);

        List<Long> userIds = Stream.concat(hierarchyIds, Stream.of(adminId)).distinct().toList();

        List<IpSpanProjection> ipSpanProjections =
                ipSpanRepository.findAllScopeByIpAndUserId(ipAsLong, userIds);

        if (CollectionUtils.isEmpty(ipSpanProjections)) {
            return Optional.empty();
        }

        Map<Scope, List<IpSpanProjection>> ipSpanByScope =
                ipSpanProjections.stream()
                        .collect(Collectors.groupingBy(IpSpanProjection::getScope));

        List<IpSpanProjection> ipSpans =
                Stream.of(Scope.SUB_CLIENT, Scope.CLIENT, Scope.GLOBAL)
                        .filter(ipSpanByScope::containsKey)
                        .map(ipSpanByScope::get)
                        .findFirst()
                        .orElse(Collections.emptyList());

        return ipSpans.stream().max(Comparator.comparing(IpSpanProjection::getCreatedAt));
    }
}
