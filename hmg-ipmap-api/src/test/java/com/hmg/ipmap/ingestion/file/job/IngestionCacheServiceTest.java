package com.hmg.ipmap.ingestion.file.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ingestion.file.job.model.IpBlockAttribute;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameRepository;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.user.UserEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionCacheServiceTest {

    @Mock private LocationRepository locationRepository;
    @Mock private LocationNameRepository locationNameRepository;
    @Mock private IpMappingRepository ipMappingRepository;

    // A fresh step-cache instance is created per test, mirroring @StepScope behaviour
    private IpBlockIngestionStepCache ipBlockStepCache;
    private IngestionCacheServiceImpl ingestionCacheService;

    @BeforeEach
    void setUp() {
        ipBlockStepCache = new IpBlockIngestionStepCache();
        ingestionCacheService =
                new IngestionCacheServiceImpl(
                        locationRepository,
                        locationNameRepository,
                        ipMappingRepository,
                        ipBlockStepCache);
        when(locationRepository.findByScopeEqualsAndGeonameIdIn(any(), any()))
                .thenReturn(List.of());
        when(ipMappingRepository.findAllByIpNotationInWithLocation(any())).thenReturn(List.of());
    }

    private IpBlock blockItem(Long geonameId) {
        IpBlock item = new IpBlock();
        item.setNetwork("1.0.0.0/24");
        item.setGeonameId(geonameId);
        item.setFileDetailId(1L);
        item.setAttribute(new IpBlockAttribute());
        return item;
    }

    private IpBlockProcessingContext context(List<IpBlock> items) {
        return new IpBlockProcessingContext(items, new UserEntity());
    }

    @Test
    void preloadCache_ShouldLoadLocationsIntoContext() {
        IpBlock item = blockItem(2077456L);
        IpBlockProcessingContext ctx = context(List.of(item));

        LocationEntity location = new LocationEntity();
        location.setId(1L);
        location.setGeonameId(2077456L);

        when(locationRepository.findByScopeEqualsAndGeonameIdIn(eq(Scope.GLOBAL), any()))
                .thenReturn(List.of(location));

        ingestionCacheService.preloadCache(ctx);

        assertThat(ctx.getLocation(2077456L)).isSameAs(location);
    }

    @Test
    void preloadCache_ShouldLoadIpMappingsIntoContext() {
        IpBlock item = blockItem(2077456L);
        IpBlockProcessingContext ctx = context(List.of(item));

        IpMappingEntity mapping = new IpMappingEntity();
        mapping.setIpNotation("1.0.0.0/24");

        when(ipMappingRepository.findAllByIpNotationInWithLocation(any()))
                .thenReturn(List.of(mapping));

        ingestionCacheService.preloadCache(ctx);

        assertThat(ctx.getIpMapping("1.0.0.0/24")).isSameAs(mapping);
    }

    @Test
    void preloadCache_ShouldFilterOutItemsWithNullGeonameId() {
        IpBlock itemWithNull = blockItem(null);
        IpBlockProcessingContext ctx = context(List.of(itemWithNull));

        ingestionCacheService.preloadCache(ctx);

        // Null geoname IDs are filtered before the DB query — no location lookup is issued
        verifyNoInteractions(locationRepository);
    }

    @Test
    void preloadCache_ShouldHandleEmptyItemList() {
        IpBlockProcessingContext ctx = context(List.of());

        ingestionCacheService.preloadCache(ctx);

        assertThat(ctx.getIpMappingCache()).isEmpty();
    }
}
