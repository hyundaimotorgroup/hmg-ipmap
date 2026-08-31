package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.error.ErrorCollector;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ingestion.file.job.model.IpBlockAttribute;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanEntity;
import com.hmg.ipmap.ipmapping.IpSpanRepository;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.IpMappingAttributeRepository;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpBlockIngestionServiceImpl implements IpBlockIngestionService {

    private final IpMappingRepository ipMappingRepository;
    private final IpMappingService ipMappingService;
    private final IpSpanRepository ipSpanRepository;
    private final JobParameter jobParameter;
    private final BatchFileDetailRepository batchFileDetailRepository;
    private final IngestionCacheService ingestionCacheService;
    private final IpMappingAttributeRepository ipMappingAttributeRepository;
    private final EntityManager entityManager;

    @Value("${app.ingestion.ip-block.chunk-size:100}")
    private Integer ipChunkSize;

    @Override
    public void registerIpBlocks(List<IpBlock> ipBlocks) {
        log.debug("Start processing {} ip blocks", ipBlocks.size());
        IpBlockProcessingContext ctx =
                new IpBlockProcessingContext(ipBlocks, jobParameter.getExecutor());
        try {
            ingestionCacheService.preloadCache(ctx);
            executeBatchIpBlock(ctx);
        } finally {
            ctx.clear();
        }
        batchUpdateToSuccess(ipBlocks);
    }

    @Override
    public void registerIpAttributes(List<IpBlockAttribute> ipBlockAttributes) {
        log.debug("Start processing {} ip block attributes", ipBlockAttributes.size());
        Set<String> ipNotations =
                ipBlockAttributes.stream()
                        .map(IpBlockAttribute::getNetwork)
                        .collect(Collectors.toSet());
        List<IpMappingEntity> ipMappings =
                ipMappingRepository.findAllLatestByIpNotations(ipNotations);
        executeBatchIpAttribute(ipMappings, ipBlockAttributes);
        batchAttributeUpdateToSuccess(ipBlockAttributes);
    }

    private void executeBatchIpBlock(IpBlockProcessingContext ctx) {
        List<IpMappingEntity> toInsert = new ArrayList<>();
        List<IpMappingEntity> toUpdate = new ArrayList<>();

        ctx.getIpBlocks().forEach(item -> classifyIpBlockItem(item, ctx, toInsert, toUpdate));

        List<IpMappingEntity> savedInserts = List.of();
        if (!toInsert.isEmpty()) {
            log.debug("Executing Batch Insert IP Block with size : {}", toInsert.size());
            savedInserts = ipMappingRepository.saveAll(toInsert);
            executeBatchIpSpan(savedInserts);
        }

        if (!toUpdate.isEmpty()) {
            log.debug("Executing Batch Update IP Block with size : {}", toUpdate.size());
            ipMappingRepository.saveAll(toUpdate);
        }

        if (toInsert.isEmpty() && toUpdate.isEmpty()) {
            return;
        }

        List<IpBlockAttribute> ipAttributes =
                ctx.getIpBlocks().stream().map(IpBlock::getAttribute).toList();

        Map<String, IpMappingEntity> processedByNotation = new HashMap<>();
        toUpdate.forEach(e -> processedByNotation.put(e.getIpNotation(), e));
        savedInserts.forEach(e -> processedByNotation.put(e.getIpNotation(), e));
        executeBatchIpAttribute(new ArrayList<>(processedByNotation.values()), ipAttributes);
    }

    private void classifyIpBlockItem(
            IpBlock ipBlock,
            IpBlockProcessingContext ctx,
            List<IpMappingEntity> toInsert,
            List<IpMappingEntity> toUpdate) {
        if (ipBlock.getGeonameId() == null) {
            ErrorCollector.add(ipBlock.getFileDetailId(), "Geoname Id is not present");
            log.warn("ip block has no geoname id: {}", ipBlock.getNetwork());
            return;
        }
        LocationEntity location = ctx.getLocation(ipBlock.getGeonameId());
        if (location == null) {
            ErrorCollector.add(
                    ipBlock.getFileDetailId(),
                    "Location not found for IP : " + ipBlock.getNetwork());
            log.warn("Location not found for IP block id : {}", ipBlock.getGeonameId());
            return;
        }
        IpMappingEntity existing = ctx.getIpMapping(ipBlock.getNetwork());
        if (existing == null) {
            toInsert.add(buildIpMappingEntity(ipBlock, ctx, location));
        } else if (isIpBlockUpdateNeeded(existing, location, ipBlock)) {
            existing.setLocation(location);
            existing.setRegisteredCountryGeonameId(ipBlock.getRegisteredCountryGeonameId());
            existing.setRepresentedCountryGeonameId(ipBlock.getRepresentedCountryGeonameId());
            existing.setUpdatedAt(Instant.now());
            existing.setUpdatedBy(jobParameter.getExecutor().getId());
            toUpdate.add(existing);
        }
    }

    private @NonNull IpMappingEntity buildIpMappingEntity(
            IpBlock ipBlock, IpBlockProcessingContext ctx, LocationEntity location) {
        IpMappingEntity ipEntity = new IpMappingEntity();
        ipEntity.setNotationType(NotationType.CIDR);
        ipEntity.setCreatedAt(jobParameter.getJobRunDate());
        ipEntity.setScope(Scope.GLOBAL);
        ipEntity.setUser(ctx.getUserEntity());
        ipEntity.setLocation(location);
        ipEntity.setIpNotation(ipBlock.getNetwork());
        ipEntity.setRegisteredCountryGeonameId(ipBlock.getRegisteredCountryGeonameId());
        ipEntity.setRepresentedCountryGeonameId(ipBlock.getRepresentedCountryGeonameId());
        return ipEntity;
    }

    private boolean isIpBlockUpdateNeeded(
            IpMappingEntity existing, LocationEntity location, IpBlock item) {
        return existing.getLocation() == null
                || !Objects.equals(existing.getLocation().getId(), location.getId())
                || !Objects.equals(
                        existing.getRegisteredCountryGeonameId(),
                        item.getRegisteredCountryGeonameId())
                || !Objects.equals(
                        existing.getRepresentedCountryGeonameId(),
                        item.getRepresentedCountryGeonameId());
    }

    private void executeBatchIpSpan(List<IpMappingEntity> ipMappingEntities) {
        log.debug("Executing Batch Insert IP Span with size : {}", ipMappingEntities.size());
        List<IpSpanEntity> spans = new ArrayList<>();
        for (IpMappingEntity ipMappingEntity : ipMappingEntities) {
            spans.addAll(ipMappingService.buildIpSpan(ipMappingEntity));

            if (spans.size() >= ipChunkSize) {
                flushAndDetachSpans(spans);
            }
        }
        if (!spans.isEmpty()) {
            flushAndDetachSpans(spans);
        }
    }

    private void flushAndDetachSpans(List<IpSpanEntity> spans) {
        ipSpanRepository.saveAll(spans);
        entityManager.flush();
        spans.forEach(entityManager::detach);
        spans.clear();
    }

    private void executeBatchIpAttribute(
            List<IpMappingEntity> ipMappings, List<IpBlockAttribute> attributes) {
        Map<String, IpMappingEntity> mapIpMapping = mapIpMapping(ipMappings);
        Map<String, List<IpMappingAttributeEntity>> ipAttributesCache =
                getAllAttributes(ipMappings);

        List<IpMappingAttributeEntity> attributesToSave = new ArrayList<>();
        for (IpBlockAttribute attribute : attributes) {
            IpMappingEntity ipMapping = mapIpMapping.get(attribute.getNetwork());
            if (ipMapping != null) {
                List<IpMappingAttributeEntity> existingAttributes =
                        ipAttributesCache.getOrDefault(
                                ipMapping.getIpNotation(), Collections.emptyList());
                attributesToSave.addAll(
                        buildIpMappingAttributes(attribute, ipMapping, existingAttributes));
            } else {
                if (attribute.getFileDetailId() != null) {
                    ErrorCollector.add(attribute.getFileDetailId(), "IP Mapping Not Found");
                }
            }
        }
        log.debug("Executing Batch Insert IP Attribute with size : {}", attributesToSave.size());
        ipMappingAttributeRepository.saveAll(attributesToSave);
    }

    private Map<String, IpMappingEntity> mapIpMapping(List<IpMappingEntity> ipMappings) {
        return ipMappings.stream()
                .collect(
                        Collectors.toMap(
                                IpMappingEntity::getIpNotation,
                                Function.identity(),
                                (a, b) -> a.getId() >= b.getId() ? a : b));
    }

    private Map<String, List<IpMappingAttributeEntity>> getAllAttributes(
            List<IpMappingEntity> ipMappings) {
        List<Long> ids = ipMappings.stream().map(IpMappingEntity::getId).toList();
        return ipMappingAttributeRepository.findAllByIpMappingIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getIpMapping().getIpNotation()));
    }

    private List<IpMappingAttributeEntity> buildIpMappingAttributes(
            IpBlockAttribute attribute,
            IpMappingEntity ipMapping,
            List<IpMappingAttributeEntity> existingAttributes) {
        List<IpMappingAttributeEntity> newAttributes = new ArrayList<>();
        attribute
                .toMap()
                .forEach(
                        (name, data) ->
                                checkAndMergeAttribute(
                                        ipMapping, existingAttributes, name, data, newAttributes));
        return newAttributes;
    }

    private void checkAndMergeAttribute(
            IpMappingEntity ipMapping,
            List<IpMappingAttributeEntity> existingAttributes,
            String objectName,
            Map<String, Object> attributesMap,
            List<IpMappingAttributeEntity> newAttributes) {
        if (attributesMap.isEmpty()) return;

        IpMappingAttributeEntity attributeEntity =
                existingAttributes.stream()
                        .filter(attr -> objectName.equals(attr.getObjectName()))
                        .findFirst()
                        .orElseGet(IpMappingAttributeEntity::new);

        if (!isAttributeUpdateNeeded(attributeEntity, attributesMap)) return;

        attributeEntity.setObjectName(objectName);
        attributeEntity.setAttributes(attributesMap);
        attributeEntity.setIpMapping(ipMapping);
        newAttributes.add(attributeEntity);
    }

    private boolean isAttributeUpdateNeeded(
            IpMappingAttributeEntity existing, Map<String, Object> incomingAttributes) {
        if (existing.getId() == null) return true;
        return !incomingAttributes.equals(existing.getAttributes());
    }

    private void batchUpdateToSuccess(List<IpBlock> ipBlocks) {
        List<Long> successIds =
                ipBlocks.stream()
                        .map(IpBlock::getFileDetailId)
                        .filter(ErrorCollector::hasNoError)
                        .toList();
        batchFileDetailRepository.updateAllToSuccessInBatch(successIds);
    }

    private void batchAttributeUpdateToSuccess(List<IpBlockAttribute> ipBlockAttributes) {
        List<Long> successIds =
                ipBlockAttributes.stream()
                        .map(IpBlockAttribute::getFileDetailId)
                        .filter(ErrorCollector::hasNoError)
                        .toList();
        batchFileDetailRepository.updateAllToSuccessInBatch(successIds);
    }
}
