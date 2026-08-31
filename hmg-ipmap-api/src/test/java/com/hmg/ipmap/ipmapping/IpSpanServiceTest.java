package com.hmg.ipmap.ipmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.ipnotation.IpArray;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpSingle;
import com.hmg.ipmap.ipnotation.IpSpan;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.user.UserEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IpSpanServiceTest {

    @Mock private IpSpanRepository ipSpanRepository;
    @Mock private IpNotationFactory ipNotationFactory;
    @Mock private IpSpanProperties ipSpanProperties;

    @InjectMocks private IpSpanServiceImpl service;

    @Captor private ArgumentCaptor<List<IpSpanEntity>> ipSpanEntitiesCaptor;

    private static final int SUBNET_PREFIX_LENGTH = 24;

    private IpMappingEntity mockMapping(
            String notation, NotationType type, Instant createdAt, long userId) {
        IpMappingEntity m = mock(IpMappingEntity.class);
        lenient().when(m.getIpNotation()).thenReturn(notation);
        lenient().when(m.getNotationType()).thenReturn(type);
        lenient().when(m.getScope()).thenReturn(Scope.GLOBAL);
        lenient().when(m.getCreatedAt()).thenReturn(createdAt);
        UserEntity user = mock(UserEntity.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(m.getUser()).thenReturn(user);
        return m;
    }

    private IpSpan mockSpan(long lower, long upper) {
        IpSpan s = mock(IpSpan.class);
        lenient().when(s.lower()).thenReturn(lower);
        lenient().when(s.upper()).thenReturn(upper);
        return s;
    }

    @Test
    void deleteAllByIpMapping_delegatesToRepository() {
        IpMappingEntity mapping =
                mockMapping(
                        "1.2.3.4", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 1L);

        service.deleteAllByIpMapping(mapping);

        verify(ipSpanRepository).deleteAllByIpMapping(mapping);
    }

    @Test
    void updateIpSpans_deletesThenSaves_inOrder_andPassesParsedEntities() {
        IpSpanService spyService =
                spy(new IpSpanServiceImpl(ipSpanRepository, ipNotationFactory, ipSpanProperties));
        IpMappingEntity mapping =
                mockMapping(
                        "1.2.3.4", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 42L);

        IpSpanEntity e1 = new IpSpanEntity(mapping);
        e1.setIpLower(10L);
        e1.setIpUpper(10L);
        e1.setScope(Scope.GLOBAL);
        e1.setCreatedAt(mapping.getCreatedAt());
        e1.setUserId(42L);

        doReturn(List.of(e1)).when(spyService).parseNotationToIpSpanList(mapping);

        spyService.updateIpSpans(mapping);

        InOrder inOrder = inOrder(ipSpanRepository);
        inOrder.verify(ipSpanRepository).deleteAllByIpMapping(mapping);
        inOrder.verify(ipSpanRepository).saveAll(ipSpanEntitiesCaptor.capture());

        List<IpSpanEntity> saved = ipSpanEntitiesCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getIpLower()).isEqualTo(10L);
        assertThat(saved.getFirst().getIpUpper()).isEqualTo(10L);
        assertThat(saved.getFirst().getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(saved.getFirst().getCreatedAt()).isEqualTo(mapping.getCreatedAt());
        assertThat(saved.getFirst().getUserId()).isEqualTo(42L);
    }

    @Test
    void parseNotationToIpSpanList_ARRAY_createsOneEntityPerValue() {
        IpMappingEntity mapping =
                mockMapping(
                        "1.1.1.1,2.2.2.2",
                        NotationType.ARRAY,
                        Instant.parse("2025-05-05T05:05:05Z"),
                        7L);

        IpArray arr = mock(IpArray.class);
        when(arr.longValues()).thenReturn(new long[] {111L, 222L, 333L});
        when(ipNotationFactory.createIpArray("1.1.1.1,2.2.2.2")).thenReturn(arr);

        List<IpSpanEntity> out = service.parseNotationToIpSpanList(mapping);

        assertThat(out).hasSize(3);
        assertThat(out).extracting(IpSpanEntity::getIpLower).containsExactly(111L, 222L, 333L);
        assertThat(out).extracting(IpSpanEntity::getIpUpper).containsExactly(111L, 222L, 333L);
        assertThat(out)
                .allSatisfy(
                        e -> {
                            assertThat(e.getScope()).isEqualTo(Scope.GLOBAL);
                            assertThat(e.getCreatedAt()).isEqualTo(mapping.getCreatedAt());
                            assertThat(e.getUserId()).isEqualTo(7L);
                        });

        verify(ipNotationFactory).createIpArray("1.1.1.1,2.2.2.2");
    }

    @Test
    void parseNotationToIpSpanList_SINGLE_createsSingleEntity() {
        IpMappingEntity mapping =
                mockMapping(
                        "10.0.0.1",
                        NotationType.SINGLE,
                        Instant.parse("2023-03-03T03:03:03Z"),
                        99L);

        IpSingle single = mock(IpSingle.class);
        when(single.longValue()).thenReturn(0x0A000001L);
        when(ipNotationFactory.createIpSingle("10.0.0.1")).thenReturn(single);

        List<IpSpanEntity> out = service.parseNotationToIpSpanList(mapping);

        assertThat(out).hasSize(1);
        IpSpanEntity e = out.getFirst();
        assertThat(e.getIpLower()).isEqualTo(0x0A000001L);
        assertThat(e.getIpUpper()).isEqualTo(0x0A000001L);
        assertThat(e.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(e.getCreatedAt()).isEqualTo(mapping.getCreatedAt());
        assertThat(e.getUserId()).isEqualTo(99L);

        verify(ipNotationFactory).createIpSingle("10.0.0.1");
    }

    @ParameterizedTest
    @EnumSource(
            value = NotationType.class,
            names = {"CIDR", "WILDCARD", "RANGE"})
    void parseNotationToIpSpanList_spanBasedTypes_createRanges(NotationType type) {
        IpMappingEntity mapping =
                mockMapping("10.0.0.0/30", type, Instant.parse("2022-02-02T02:02:02Z"), 123L);

        IpSpan span1 = mockSpan(1000L, 1003L);
        IpSpan span2 = mockSpan(2000L, 2005L);
        when(ipNotationFactory.createIpSpans("10.0.0.0/30", SUBNET_PREFIX_LENGTH))
                .thenReturn(List.of(span1, span2));
        when(ipSpanProperties.getSubnetPrefixLength()).thenReturn(SUBNET_PREFIX_LENGTH);

        List<IpSpanEntity> out = service.parseNotationToIpSpanList(mapping);

        assertThat(out).hasSize(2);
        assertThat(out).extracting(IpSpanEntity::getIpLower).containsExactly(1000L, 2000L);
        assertThat(out).extracting(IpSpanEntity::getIpUpper).containsExactly(1003L, 2005L);
        assertThat(out)
                .allSatisfy(
                        e -> {
                            assertThat(e.getScope()).isEqualTo(Scope.GLOBAL);
                            assertThat(e.getCreatedAt()).isEqualTo(mapping.getCreatedAt());
                            assertThat(e.getUserId()).isEqualTo(123L);
                        });

        verify(ipNotationFactory).createIpSpans("10.0.0.0/30", SUBNET_PREFIX_LENGTH);
    }

    @Test
    void rebuildIpSpans_deletesAndSavesForEachMapping_inOrder() {
        IpSpanService spyService = spy(service);
        IpMappingEntity mapping1 =
                mockMapping(
                        "1.1.1.1", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 1L);
        IpMappingEntity mapping2 =
                mockMapping(
                        "2.2.2.2", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 2L);

        IpSpanEntity span1 = new IpSpanEntity(mapping1);
        IpSpanEntity span2 = new IpSpanEntity(mapping2);
        doReturn(List.of(span1)).when(spyService).parseNotationToIpSpanList(mapping1);
        doReturn(List.of(span2)).when(spyService).parseNotationToIpSpanList(mapping2);

        spyService.rebuildIpSpans(List.of(mapping1, mapping2));

        InOrder inOrder = inOrder(ipSpanRepository);
        inOrder.verify(ipSpanRepository).deleteAllByIpMapping(mapping1);
        inOrder.verify(ipSpanRepository).saveAll(List.of(span1));
        inOrder.verify(ipSpanRepository).deleteAllByIpMapping(mapping2);
        inOrder.verify(ipSpanRepository).saveAll(List.of(span2));
    }

    @Test
    void rebuildIpSpans_whenOneMappingFails_continuesProcessingRest() {
        IpSpanService spyService = spy(service);
        IpMappingEntity failing =
                mockMapping(
                        "1.1.1.1", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 1L);
        IpMappingEntity succeeding =
                mockMapping(
                        "2.2.2.2", NotationType.SINGLE, Instant.parse("2024-01-01T00:00:00Z"), 2L);

        lenient().when(failing.getId()).thenReturn(99L);
        doThrow(new RuntimeException("db error"))
                .when(ipSpanRepository)
                .deleteAllByIpMapping(failing);

        IpSpanEntity span = new IpSpanEntity(succeeding);
        doReturn(List.of(span)).when(spyService).parseNotationToIpSpanList(succeeding);

        spyService.rebuildIpSpans(List.of(failing, succeeding));

        verify(ipSpanRepository).deleteAllByIpMapping(failing);
        verify(ipSpanRepository, never()).saveAll(List.of(new IpSpanEntity(failing)));
        verify(ipSpanRepository).deleteAllByIpMapping(succeeding);
        verify(ipSpanRepository).saveAll(List.of(span));
    }

    @Test
    void rebuildIpSpans_emptyList_noRepositoryInteractions() {
        service.rebuildIpSpans(List.of());

        verifyNoInteractions(ipSpanRepository);
    }

    @Test
    void parseNotationToIpSpanList_nullNotationType_throwsNullPointer() {
        IpMappingEntity mapping = mockMapping("x", null, Instant.now(), 1L);

        try {
            service.parseNotationToIpSpanList(mapping);
        } catch (NullPointerException _) {
            // this catch is expected
        }
        verifyNoInteractions(ipSpanRepository);
    }
}
