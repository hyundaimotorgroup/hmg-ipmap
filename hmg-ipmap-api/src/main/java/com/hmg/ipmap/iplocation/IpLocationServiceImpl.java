package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.iplocation.dto.IpMappingDomainData;
import com.hmg.ipmap.iplocation.response.TemplateResponseStrategyFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpLocationServiceImpl implements IpLocationService {

    private final List<IpLocationResolver> resolvers;
    private final TemplateResponseStrategyFactory strategyFactory;

    private static final IpLocationDomainData NOT_FOUND_MARKER;

    static {
        NOT_FOUND_MARKER = new IpLocationDomainData();
        NOT_FOUND_MARKER.setIpNotFound(true);
        NOT_FOUND_MARKER.setIpMapping(
                new IpMappingDomainData("__NOT_FOUND__", null, null, null, null));
    }

    // Caches the serialized JSON string so Spring MVC writes it directly to the response
    // without re-serializing the DTO on every cache hit.
    @Cacheable(
            value = "ipLocations",
            key =
                    "T(com.hmg.ipmap.common.context.UserContextHolder).get().id() + ':' + T(com.hmg.ipmap.common.context.UserContextHolder).get().responseTemplate() + ':' + #ip",
            cacheManager = "caffeineCacheManager",
            condition = "@caffeineProperties.ipLocationEnabled",
            sync = true)
    @Override
    public IpLocationResult findLocationByIpAddress(String ip) {
        IpLocationDomainData domainData = buildLocationDomainData(ip);
        String body =
                strategyFactory.get(UserContextHolder.get().responseTemplate()).format(domainData);
        return new IpLocationResult(
                body,
                domainData.isIpNotFound(),
                Optional.ofNullable(domainData.getIpMapping()).map(IpMappingDomainData::getScope));
    }

    private IpLocationDomainData buildLocationDomainData(String ip) {
        return resolvers.stream()
                .map(resolver -> resolver.resolve(ip))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(NOT_FOUND_MARKER);
    }
}
