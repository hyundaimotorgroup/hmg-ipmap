package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.ipnotation.IpArray;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpSingle;
import com.hmg.ipmap.ipnotation.IpSpan;
import com.hmg.ipmap.ipnotation.NotationType;
import java.util.List;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for parsing IP notations into {@link IpSpanEntity} lists and keeping the {@code ip_span}
 * table in sync with {@link IpMappingEntity} changes.
 *
 * <p>Supports all notation types: single addresses, comma-separated arrays, CIDR blocks, hyphen
 * ranges, and wildcard patterns. Span granularity is controlled by the configured subnet prefix
 * length from {@link com.hmg.ipmap.common.config.IpSpanProperties}.
 */
@Slf4j
@Service
public class IpSpanServiceImpl implements IpSpanService {
    private final IpSpanRepository ipSpanRepository;
    private final IpNotationFactory ipNotationFactory;
    private final IpSpanProperties ipSpanProperties;

    public IpSpanServiceImpl(
            IpSpanRepository ipSpanRepository,
            IpNotationFactory ipNotationFactory,
            IpSpanProperties ipSpanProperties) {
        this.ipSpanRepository = ipSpanRepository;
        this.ipNotationFactory = ipNotationFactory;
        this.ipSpanProperties = ipSpanProperties;
    }

    @Transactional
    @Override
    public void updateIpSpans(IpMappingEntity ipMappingEntity) {
        ipSpanRepository.deleteAllByIpMapping(ipMappingEntity);
        // Expand notation → ip address list
        List<IpSpanEntity> ipAddressEntities = parseNotationToIpSpanList(ipMappingEntity);
        ipSpanRepository.saveAll(ipAddressEntities);
    }

    @Override
    public List<IpSpanEntity> parseNotationToIpSpanList(IpMappingEntity ipMapping) {
        String ipNotation = ipMapping.getIpNotation();
        NotationType notationType = ipMapping.getNotationType();
        switch (notationType) {
            case ARRAY -> {
                IpArray ipArray = ipNotationFactory.createIpArray(ipNotation);
                return LongStream.of(ipArray.longValues())
                        .mapToObj(
                                ipLongValue -> createIpSpanEntityBySingleIp(ipMapping, ipLongValue))
                        .toList();
            }
            case SINGLE -> {
                IpSingle ipSingle = ipNotationFactory.createIpSingle(ipNotation);
                IpSpanEntity ipAddress =
                        createIpSpanEntityBySingleIp(ipMapping, ipSingle.longValue());
                return List.of(ipAddress);
            }
            case CIDR, WILDCARD, RANGE -> {
                List<IpSpan> ipSpans =
                        ipNotationFactory.createIpSpans(
                                ipNotation, ipSpanProperties.getSubnetPrefixLength());
                return ipSpans.stream()
                        .map(
                                ipSpan -> {
                                    IpSpanEntity ipAddress = new IpSpanEntity(ipMapping);
                                    ipAddress.setIpLower(ipSpan.lower());
                                    ipAddress.setIpUpper(ipSpan.upper());
                                    ipAddress.setScope(ipMapping.getScope());
                                    ipAddress.setCreatedAt(ipMapping.getCreatedAt());
                                    ipAddress.setUserId(ipMapping.getUser().getId());
                                    ipAddress.setValidPeriod(ipMapping.getValidPeriod());
                                    return ipAddress;
                                })
                        .toList();
            }

            default ->
                    throw new UnsupportedOperationException(
                            "Unsupported NotationType:" + notationType);
        }
    }

    @Transactional
    @Override
    public void deleteAllByIpMapping(IpMappingEntity ipMappingEntity) {
        ipSpanRepository.deleteAllByIpMapping(ipMappingEntity);
    }

    @Transactional
    @Override
    public void rebuildIpSpans(List<IpMappingEntity> ipMappings) {
        for (IpMappingEntity ipMapping : ipMappings) {
            try {
                ipSpanRepository.deleteAllByIpMapping(ipMapping);
                List<IpSpanEntity> newSpans = parseNotationToIpSpanList(ipMapping);
                ipSpanRepository.saveAll(newSpans);
            } catch (Exception e) {
                log.error("Failed to rebuild ip spans for ipMapping id={}", ipMapping.getId(), e);
            }
        }
    }

    private IpSpanEntity createIpSpanEntityBySingleIp(IpMappingEntity source, long ipLongValue) {
        IpSpanEntity ipAddress = new IpSpanEntity(source);
        ipAddress.setIpLower(ipLongValue);
        ipAddress.setIpUpper(ipLongValue);
        ipAddress.setScope(source.getScope());
        ipAddress.setCreatedAt(source.getCreatedAt());
        ipAddress.setUserId(source.getUser().getId());
        ipAddress.setValidPeriod(source.getValidPeriod());
        return ipAddress;
    }
}
