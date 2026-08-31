package com.hmg.ipmap.cache.event;

import com.hmg.ipmap.common.CachedEntity;
import com.hmg.ipmap.common.entity.AuditableEntity;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheEntityListener {

    private final ApplicationEventPublisher eventPublisher;
    private final CacheEntityConverter entityConverter;

    public CacheEntityListener(
            ApplicationEventPublisher eventPublisher, CacheEntityConverter entityConverter) {
        this.eventPublisher = eventPublisher;
        this.entityConverter = entityConverter;
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        if (entity instanceof CachedEntity cachedEntity) {
            publishEvent("UPDATE", cachedEntity, resolveTimestamp(entity));
        }
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        if (entity instanceof CachedEntity cachedEntity) {
            publishEvent("UPDATE", cachedEntity, resolveTimestamp(entity));
        }
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        if (entity instanceof CachedEntity cachedEntity) {
            publishEvent("DELETE", cachedEntity, resolveTimestamp(entity));
        }
    }

    private Instant resolveTimestamp(Object entity) {
        if (entity instanceof AuditableEntity a && a.getUpdatedAt() != null) {
            return a.getUpdatedAt();
        }
        log.debug("Resolving timestamp using current instant");
        return Instant.now();
    }

    private void publishEvent(String action, CachedEntity entity, Instant sourceTimestamp) {
        if (eventPublisher == null) {
            log.warn(
                    "EventPublisher is null, cannot publish cache event for action: {} on entity: {}",
                    action,
                    entity.getClass().getSimpleName());
            return;
        }

        if (entityConverter == null) {
            log.warn(
                    "EntityConverter is null, cannot publish cache event for action: {} on entity: {}",
                    action,
                    entity.getClass().getSimpleName());
            return;
        }

        String tableName = entity.tableName();
        Object cacheDto = entityConverter.convertToCacheDto(entity);
        if (cacheDto != null) {
            eventPublisher.publishEvent(
                    new CacheUpdateEvent(action, tableName, cacheDto, sourceTimestamp));
        }
    }
}
