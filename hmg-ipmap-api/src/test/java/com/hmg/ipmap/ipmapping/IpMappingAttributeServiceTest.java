package com.hmg.ipmap.ipmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.ipmapping.dto.IpMappingRequestDto;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.IpMappingAttributeRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IpMappingAttributeServiceTest {

    @Mock private IpMappingAttributeRepository ipMappingAttributeRepository;

    @InjectMocks private IpMappingAttributeServiceImpl service;

    @Captor private ArgumentCaptor<List<IpMappingAttributeEntity>> savedAttrsCaptor;

    private IpMappingEntity entity(long id) {
        IpMappingEntity e = new IpMappingEntity();
        e.setId(id);
        return e;
    }

    // =========================================================================
    // deleteAllByIpMapping
    // =========================================================================
    @Nested
    class DeleteAllByIpMappingTests {

        @Test
        void delegatesToRepository() {
            IpMappingEntity e = entity(1L);
            service.deleteAllByIpMapping(e);
            verify(ipMappingAttributeRepository).deleteAllByIpMapping(e);
        }
    }

    // =========================================================================
    // replaceAttributes
    // =========================================================================
    @Nested
    class ReplaceAttributesTests {

        @Test
        void deletesBeforeSaving_inOrder() {
            IpMappingEntity e = entity(2L);
            IpMappingRequestDto req =
                    new IpMappingRequestDto(
                            "1.2.3.4",
                            null,
                            Map.of("TRAITS", Map.of("asn", 1234)),
                            null,
                            null,
                            null);

            service.replaceAttributes(req, e);

            InOrder inOrder = inOrder(ipMappingAttributeRepository);
            inOrder.verify(ipMappingAttributeRepository).deleteAllByIpMapping(e);
            inOrder.verify(ipMappingAttributeRepository).saveAll(any());
        }

        @Test
        void savesTraitsPostalAndLocation_whenAllPresent() {
            IpMappingEntity e = entity(3L);
            IpMappingRequestDto req =
                    new IpMappingRequestDto(
                            "1.2.3.4",
                            null,
                            Map.of(
                                    "TRAITS", Map.of("asn", 1),
                                    "LOCATION", Map.of("lat", -6.2),
                                    "POSTAL", Map.of("code", "10110")),
                            null,
                            null,
                            null);

            service.replaceAttributes(req, e);

            verify(ipMappingAttributeRepository).saveAll(savedAttrsCaptor.capture());
            List<IpMappingAttributeEntity> saved = savedAttrsCaptor.getValue();

            assertThat(saved).hasSize(3);
            assertThat(saved)
                    .extracting(IpMappingAttributeEntity::getObjectName)
                    .containsExactlyInAnyOrder("TRAITS", "POSTAL", "LOCATION");
            assertThat(saved).allSatisfy(a -> assertThat(a.getIpMapping()).isEqualTo(e));
        }

        @Test
        void noSaveCall_whenAllAttributeMapsAreNull() {
            IpMappingEntity e = entity(4L);
            IpMappingRequestDto req =
                    new IpMappingRequestDto("1.2.3.4", null, null, null, null, null);

            service.replaceAttributes(req, e);

            verify(ipMappingAttributeRepository).deleteAllByIpMapping(e);
            verify(ipMappingAttributeRepository, never()).saveAll(anyList());
        }

        @Test
        void savesOnlyPresentAttributes_whenSomeAreNull() {
            IpMappingEntity e = entity(5L);
            IpMappingRequestDto req =
                    new IpMappingRequestDto(
                            "1.2.3.4",
                            null,
                            Map.of("TRAITS", Map.of("asn", 99)),
                            null,
                            null,
                            null); // only traits

            service.replaceAttributes(req, e);

            verify(ipMappingAttributeRepository).saveAll(savedAttrsCaptor.capture());
            List<IpMappingAttributeEntity> saved = savedAttrsCaptor.getValue();

            assertThat(saved).hasSize(1);
            assertThat(saved.getFirst().getObjectName()).isEqualTo("TRAITS");
        }
    }

    // =========================================================================
    // fetchByIpMappingIds
    // =========================================================================
    @Nested
    class FetchByIpMappingIdsTests {

        @Test
        void returnsRepositoryResult() {
            IpMappingAttributeEntity attr = new IpMappingAttributeEntity();
            when(ipMappingAttributeRepository.findAllByIpMappingIdIn(List.of(1L, 2L)))
                    .thenReturn(List.of(attr));

            List<IpMappingAttributeEntity> result = service.fetchByIpMappingIds(List.of(1L, 2L));

            assertThat(result).containsExactly(attr);
        }
    }
}
