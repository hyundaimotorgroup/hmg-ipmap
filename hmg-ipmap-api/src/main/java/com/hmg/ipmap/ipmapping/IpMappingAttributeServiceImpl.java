package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.IpMappingAttributeRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for all {@link IpMappingAttributeEntity} lifecycle operations.
 *
 * <p>Extracts attribute-management concerns (building, persisting, replacing, and fetching
 * attribute entities) that previously lived inside {@link IpMappingServiceImpl}, keeping that class
 * focused on high-level orchestration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IpMappingAttributeServiceImpl implements IpMappingAttributeService {

    private final IpMappingAttributeRepository ipMappingAttributeRepository;

    @Transactional
    @Override
    public void deleteAllByIpMapping(IpMappingEntity entity) {
        ipMappingAttributeRepository.deleteAllByIpMapping(entity);
    }

    @Transactional
    @Override
    public void replaceAttributes(IpMappingRequestDto request, IpMappingEntity entity) {
        log.trace("Replacing attributes for ipMapping id={}", entity.getId());
        ipMappingAttributeRepository.deleteAllByIpMapping(entity);

        List<IpMappingAttributeEntity> attributes = buildAttributeEntities(request, entity);
        if (!attributes.isEmpty()) {
            ipMappingAttributeRepository.saveAll(attributes);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<IpMappingAttributeEntity> fetchByIpMappingIds(List<Long> ipMappingIds) {
        return ipMappingAttributeRepository.findAllByIpMappingIdIn(ipMappingIds);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<IpMappingAttributeEntity> buildAttributeEntities(
            IpMappingRequestDto request, IpMappingEntity entity) {
        Map<String, Map<String, Object>> attributes = request.attributes();
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        return attributes.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(
                        entry -> {
                            IpMappingAttributeEntity attr = new IpMappingAttributeEntity();
                            attr.setIpMapping(entity);
                            attr.setObjectName(entry.getKey());
                            attr.setAttributes(entry.getValue());
                            return attr;
                        })
                .toList();
    }
}
