package com.hmg.ipmap.ingestion.file.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.ingestion.file.job.error.ErrorCollector;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ingestion.file.job.model.IpBlockAttribute;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.ipmapping.IpMappingService;
import com.hmg.ipmap.ipmapping.IpSpanEntity;
import com.hmg.ipmap.ipmapping.IpSpanRepository;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.location.IpMappingAttributeRepository;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.user.UserEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpBlockIngestionServiceTest {

    @Mock private IpMappingRepository ipMappingRepository;
    @Mock private IpMappingService ipMappingService;
    @Mock private IpSpanRepository ipSpanRepository;
    @Mock private JobParameter jobParameter;
    @Mock private BatchFileDetailRepository batchFileDetailRepository;
    @Mock private IngestionCacheService ingestionCacheService;
    @Mock private IpMappingAttributeRepository ipMappingAttributeRepository;

    @Mock
    @SuppressWarnings("unused")
    private EntityManager entityManager;

    @Captor private ArgumentCaptor<List<IpMappingEntity>> ipMappingCaptor;
    @Captor private ArgumentCaptor<List<Long>> successIdsCaptor;

    @InjectMocks private IpBlockIngestionServiceImpl ipBlockIngestionService;

    private static final Long GEONAME_AUSTRALIA = 2077456L;
    private static final Long GEONAME_SOUTH_KOREA = 1835841L;
    private static final Long GEONAME_FRANCE = 2988389L;
    private static final Long GEONAME_UNKNOWN = 9999999L;

    private UserEntity executor;
    private LocationEntity location;

    @BeforeEach
    void setUp() {
        ErrorCollector.startChunk();
        ReflectionTestUtils.setField(ipBlockIngestionService, "ipChunkSize", 100);

        executor = new UserEntity();
        executor.setId(1L);
        executor.setUserType(UserType.ADMIN);

        location = new LocationEntity();
        location.setId(1L);

        when(jobParameter.getExecutor()).thenReturn(executor);
        when(jobParameter.getJobRunDate()).thenReturn(Instant.now());
        when(ipMappingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ipMappingService.buildIpSpan(any())).thenReturn(List.of(new IpSpanEntity()));
        when(ipMappingAttributeRepository.findAllByIpMappingIdIn(any())).thenReturn(List.of());
    }

    // --- helpers ---

    private IpBlock buildItem(Long fileDetailId, String network, Long geonameId) {
        IpBlock item = new IpBlock();
        item.setFileDetailId(fileDetailId);
        item.setNetwork(network);
        item.setGeonameId(geonameId);
        item.setRegisteredCountryGeonameId(6252001L);
        item.setRepresentedCountryGeonameId(6252001L);
        item.setAttribute(new IpBlockAttribute());
        return item;
    }

    private void preloadLocationInContext(Long geonameId, LocationEntity loc) {
        doAnswer(
                        inv -> {
                            IpBlockProcessingContext ctx = inv.getArgument(0);
                            ctx.putLocation(geonameId, loc);
                            return null;
                        })
                .when(ingestionCacheService)
                .preloadCache(any(IpBlockProcessingContext.class));
    }

    // --- processIpBlocks tests ---

    @Test
    void registerIpBlocks_ShouldSaveIpMappingSpanAndAttributeWhenLocationFound() {
        IpBlock item = buildItem(1L, "1.0.0.0/24", GEONAME_AUSTRALIA);
        preloadLocationInContext(GEONAME_AUSTRALIA, location);

        ipBlockIngestionService.registerIpBlocks(List.of(item));

        verify(ipMappingRepository).saveAll(ipMappingCaptor.capture());
        assertThat(ipMappingCaptor.getValue()).hasSize(1);
        verify(ipSpanRepository).saveAll(anyList());
        verify(ipMappingAttributeRepository).saveAll(any());
        verify(batchFileDetailRepository).updateAllToSuccessInBatch(successIdsCaptor.capture());
        assertThat(successIdsCaptor.getValue()).containsExactly(1L);
    }

    @Test
    void registerIpBlocks_ShouldSetCorrectFieldsOnIpMappingEntity() {
        IpBlock item = buildItem(1L, "1.0.0.0/24", GEONAME_SOUTH_KOREA);
        item.setRegisteredCountryGeonameId(6252001L);
        item.setRepresentedCountryGeonameId(1861060L);
        preloadLocationInContext(GEONAME_SOUTH_KOREA, location);

        ipBlockIngestionService.registerIpBlocks(List.of(item));

        verify(ipMappingRepository).saveAll(ipMappingCaptor.capture());
        IpMappingEntity saved = ipMappingCaptor.getValue().getFirst();
        assertThat(saved.getIpNotation()).isEqualTo("1.0.0.0/24");
        assertThat(saved.getNotationType()).isEqualTo(NotationType.CIDR);
        assertThat(saved.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(saved.getUser()).isEqualTo(executor);
        assertThat(saved.getLocation()).isEqualTo(location);
        assertThat(saved.getRegisteredCountryGeonameId()).isEqualTo(6252001L);
        assertThat(saved.getRepresentedCountryGeonameId()).isEqualTo(1861060L);
    }

    @Test
    void registerIpBlocks_ShouldSkipItemAndRecordErrorWhenLocationNotFound() {
        IpBlock item = buildItem(1L, "1.0.0.0/24", GEONAME_UNKNOWN);

        ipBlockIngestionService.registerIpBlocks(List.of(item));

        verify(ipMappingRepository, never()).saveAll(any());
        verify(ipSpanRepository, never()).saveAll(any());
        verify(ipMappingAttributeRepository, never()).saveAll(any());
        verify(batchFileDetailRepository).updateAllToSuccessInBatch(successIdsCaptor.capture());
        assertThat(successIdsCaptor.getValue()).isEmpty();
    }

    @Test
    void registerIpBlocks_ShouldDoNothingWhenListIsEmpty() {
        ipBlockIngestionService.registerIpBlocks(new ArrayList<>());

        verify(ingestionCacheService).preloadCache(any(IpBlockProcessingContext.class));
        verify(ipMappingRepository, never()).saveAll(any());
        verify(ipSpanRepository, never()).saveAll(any());
        verify(ipMappingAttributeRepository, never()).saveAll(any());
        verify(batchFileDetailRepository).updateAllToSuccessInBatch(successIdsCaptor.capture());
        assertThat(successIdsCaptor.getValue()).isEmpty();
    }

    @Test
    void registerIpBlocks_ShouldOnlySaveItemsWhoseLocationWasFound() {
        IpBlock found = buildItem(1L, "1.0.0.0/24", GEONAME_FRANCE);
        IpBlock notFound = buildItem(2L, "2.0.0.0/24", GEONAME_UNKNOWN);
        preloadLocationInContext(GEONAME_FRANCE, location);

        ipBlockIngestionService.registerIpBlocks(List.of(found, notFound));

        verify(ipMappingRepository).saveAll(ipMappingCaptor.capture());
        assertThat(ipMappingCaptor.getValue())
                .hasSize(1)
                .extracting(IpMappingEntity::getIpNotation)
                .containsExactly("1.0.0.0/24");

        verify(batchFileDetailRepository).updateAllToSuccessInBatch(successIdsCaptor.capture());
        assertThat(successIdsCaptor.getValue()).containsExactly(1L);
    }

    @Test
    void registerIpBlocks_ShouldBuildIpSpanForEachSavedIpMapping() {
        IpBlock item1 = buildItem(1L, "1.0.0.0/24", GEONAME_SOUTH_KOREA);
        IpBlock item2 = buildItem(2L, "2.0.0.0/24", GEONAME_SOUTH_KOREA);
        preloadLocationInContext(GEONAME_SOUTH_KOREA, location);

        IpSpanEntity span1 = new IpSpanEntity();
        IpSpanEntity span2 = new IpSpanEntity();
        when(ipMappingService.buildIpSpan(any()))
                .thenReturn(List.of(span1))
                .thenReturn(List.of(span2));

        ipBlockIngestionService.registerIpBlocks(List.of(item1, item2));

        verify(ipMappingService, Mockito.times(2)).buildIpSpan(any(IpMappingEntity.class));
        // spans accumulate across all mappings and are flushed in one batch at the end
        // (2 spans < chunk threshold of 100, so the mid-loop flush never fires)
        verify(ipSpanRepository, Mockito.times(1)).saveAll(anyList());
    }

    @Test
    void registerIpBlocks_ShouldUpdateExistingMappingWhenItsLocationIsNull() {
        IpMappingEntity existingWithNullLocation = new IpMappingEntity();
        existingWithNullLocation.setId(99L);
        existingWithNullLocation.setIpNotation("1.0.0.0/24");

        IpBlock item = buildItem(1L, "1.0.0.0/24", GEONAME_AUSTRALIA);

        doAnswer(
                        inv -> {
                            IpBlockProcessingContext ctx = inv.getArgument(0);
                            ctx.putLocation(GEONAME_AUSTRALIA, location);
                            ctx.putIpMapping("1.0.0.0/24", existingWithNullLocation);
                            return null;
                        })
                .when(ingestionCacheService)
                .preloadCache(any(IpBlockProcessingContext.class));

        ipBlockIngestionService.registerIpBlocks(List.of(item));

        verify(ipMappingRepository).saveAll(ipMappingCaptor.capture());
        assertThat(ipMappingCaptor.getValue()).contains(existingWithNullLocation);
        assertThat(existingWithNullLocation.getLocation()).isEqualTo(location);
    }

    @Test
    void registerIpBlocks_ShouldPropagateExceptionAndSkipBatchUpdateWhenPreloadCacheFails() {
        IpBlock item = buildItem(1L, "1.0.0.0/24", GEONAME_AUSTRALIA);

        doThrow(new RuntimeException("Cache failure"))
                .when(ingestionCacheService)
                .preloadCache(any(IpBlockProcessingContext.class));

        List<IpBlock> items = List.of(item);
        assertThatThrownBy(() -> ipBlockIngestionService.registerIpBlocks(items))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cache failure");

        verify(ipMappingRepository, never()).saveAll(any());
        verify(batchFileDetailRepository, never()).updateAllToSuccessInBatch(any());
    }
}
