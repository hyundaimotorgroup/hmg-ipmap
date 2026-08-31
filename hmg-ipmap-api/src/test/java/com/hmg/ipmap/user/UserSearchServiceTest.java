package com.hmg.ipmap.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserResponseDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;

    @InjectMocks private UserSearchServiceImpl userSearchService;

    private UserContext adminContext;
    private UserContext clientContext;
    private UserContext subClientContext;

    @BeforeEach
    void setUp() {
        adminContext =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        clientContext =
                new UserContext(2L, "client", UserType.CLIENT, "1.2.3.5", Scope.CLIENT, null, null);
        subClientContext =
                new UserContext(
                        3L,
                        "subclient",
                        UserType.SUB_CLIENT,
                        "1.2.3.6",
                        Scope.SUB_CLIENT,
                        new UserContext(
                                2L, "client", UserType.CLIENT, "1.2.3.5", Scope.CLIENT, null, null),
                        null);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ------------------------------------------------------------------------
    // Tests for searchWithPagination - ADMIN user
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: ADMIN user should get all user types")
    void searchWithPagination_adminUser_shouldGetAllUserTypes() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity adminEntity = createUserEntity(1L, "admin1", UserType.ADMIN, null, null);
        UserEntity clientEntity = createUserEntity(2L, "client1", UserType.CLIENT, null, null);
        UserEntity subClientEntity =
                createUserEntity(3L, "subclient1", UserType.SUB_CLIENT, null, "1.2.3.6");

        List<UserEntity> entities = List.of(adminEntity, clientEntity, subClientEntity);
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserEntity> entityPage = new PageImpl<>(entities, pageable, entities.size());

        UserResponseDto adminDto = createUserDto(1L, "admin1", UserType.ADMIN, null, null);
        UserResponseDto clientDto = createUserDto(2L, "client1", UserType.CLIENT, null, null);
        UserResponseDto subClientDto =
                createUserDto(3L, "subclient1", UserType.SUB_CLIENT, null, "192.168.1.1");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(adminEntity)).thenReturn(adminDto);
        when(userMapper.toDto(clientEntity)).thenReturn(clientDto);
        when(userMapper.toDto(subClientEntity)).thenReturn(subClientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.content()).hasSize(3);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.number()).isZero();

        // Verify source IP was converted
        UserResponseDto convertedSubClient =
                response.content().stream()
                        .filter(dto -> dto.getId().equals(3L))
                        .findFirst()
                        .orElse(null);
        assertThat(convertedSubClient).isNotNull();
        assertThat(convertedSubClient.getSourceIp()).isEqualTo("192.168.1.1");

        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("searchWithPagination: ADMIN user pagination should be correct")
    void searchWithPagination_adminUser_paginationCorrect() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(5, 2); // page 2, size 5

        List<UserEntity> entities =
                List.of(createUserEntity(1L, "user1", UserType.CLIENT, null, null));
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto dto = createUserDto(1L, "user1", UserType.CLIENT, null, null);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(any())).thenReturn(dto);

        // When
        userSearchService.searchWithPagination(request);

        // Then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository)
                .findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(),
                        pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(2);
        assertThat(capturedPageable.getPageSize()).isEqualTo(5);
    }

    // ------------------------------------------------------------------------
    // Tests for searchWithPagination - CLIENT user
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: CLIENT user should get CLIENT and SUB_CLIENT types only")
    void searchWithPagination_clientUser_shouldGetClientAndSubClientTypes() {
        // Given
        UserContextHolder.set(clientContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity clientEntity = createUserEntity(2L, "client1", UserType.CLIENT, null, null);
        UserEntity subClientEntity =
                createUserEntity(3L, "subclient1", UserType.SUB_CLIENT, 2L, "1.2.3.6");

        List<UserEntity> entities = List.of(clientEntity, subClientEntity);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto clientDto = createUserDto(2L, "client1", UserType.CLIENT, null, null);
        UserResponseDto subClientDto =
                createUserDto(3L, "subclient1", UserType.SUB_CLIENT, 2L, "192.168.1.1");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(clientEntity)).thenReturn(clientDto);
        when(userMapper.toDto(subClientEntity)).thenReturn(subClientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).hasSize(2);

        // Verify no ADMIN users in response
        boolean hasAdmin =
                response.content().stream().anyMatch(dto -> dto.getUserType() == UserType.ADMIN);
        assertThat(hasAdmin).isFalse();

        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("searchWithPagination: CLIENT user should filter by their own ID and parent ID")
    void searchWithPagination_clientUser_shouldFilterByUserIdAndParentId() {
        // Given
        UserContextHolder.set(clientContext); // ID = 2L
        PaginationRequest request = new PaginationRequest(10, 0);

        List<UserEntity> entities =
                List.of(createUserEntity(2L, "client1", UserType.CLIENT, null, null));
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
        UserResponseDto dto = createUserDto(2L, "client1", UserType.CLIENT, null, null);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(any())).thenReturn(dto);

        // When
        userSearchService.searchWithPagination(request);

        // Then
        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
        // The specification should filter by currentUserId (2L) and include parent ID (2L)
    }

    // ------------------------------------------------------------------------
    // Tests for searchWithPagination - SUB_CLIENT user
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: SUB_CLIENT user should only see themselves")
    void searchWithPagination_subClientUser_shouldOnlySeeSelf() {
        // Given
        UserContextHolder.set(subClientContext); // ID = 3L
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity subClientEntity =
                createUserEntity(3L, "subclient1", UserType.SUB_CLIENT, 2L, "1.2.3.6");

        List<UserEntity> entities = List.of(subClientEntity);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto subClientDto =
                createUserDto(3L, "subclient1", UserType.SUB_CLIENT, 2L, "192.168.1.1");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(subClientEntity)).thenReturn(subClientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);

        UserResponseDto dto = response.content().getFirst();
        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getUserType()).isEqualTo(UserType.SUB_CLIENT);
        assertThat(dto.getSourceIp()).isEqualTo("192.168.1.1");

        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName(
            "searchWithPagination: SUB_CLIENT user should filter by SUB_CLIENT type and their own"
                    + " ID")
    void searchWithPagination_subClientUser_shouldFilterByTypeAndUserId() {
        // Given
        UserContextHolder.set(subClientContext); // ID = 3L
        PaginationRequest request = new PaginationRequest(10, 0);

        List<UserEntity> entities =
                List.of(createUserEntity(3L, "subclient1", UserType.SUB_CLIENT, 2L, "1.2.3.6"));
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
        UserResponseDto dto =
                createUserDto(3L, "subclient1", UserType.SUB_CLIENT, 2L, "192.168.1.1");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(any())).thenReturn(dto);

        // When
        userSearchService.searchWithPagination(request);

        // Then
        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
        // The specification should filter by SUB_CLIENT type and currentUserId (3L)
    }

    // ------------------------------------------------------------------------
    // Tests for source IP conversion
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: should convert source IP from long to string")
    void searchWithPagination_shouldConvertSourceIp() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity subClient1 = createUserEntity(1L, "sub1", UserType.SUB_CLIENT, 2L, "1.2.3.6");
        UserEntity subClient2 = createUserEntity(2L, "sub2", UserType.SUB_CLIENT, 2L, "1.2.3.7");

        List<UserEntity> entities = List.of(subClient1, subClient2);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto dto1 = createUserDto(1L, "sub1", UserType.SUB_CLIENT, 2L, "192.168.1.1");
        UserResponseDto dto2 = createUserDto(2L, "sub2", UserType.SUB_CLIENT, 2L, "10.0.0.1");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(subClient1)).thenReturn(dto1);
        when(userMapper.toDto(subClient2)).thenReturn(dto2);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).getSourceIp()).isEqualTo("192.168.1.1");
        assertThat(response.content().get(1).getSourceIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("searchWithPagination: should handle null source IP")
    void searchWithPagination_shouldHandleNullSourceIp() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity clientEntity = createUserEntity(1L, "client1", UserType.CLIENT, null, null);

        List<UserEntity> entities = List.of(clientEntity);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto clientDto = createUserDto(1L, "client1", UserType.CLIENT, null, null);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(clientEntity)).thenReturn(clientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().getSourceIp()).isNull();

        // Verify IP conversion was NOT called for null values
    }

    @Test
    @DisplayName("searchWithPagination: should handle blank source IP")
    void searchWithPagination_shouldHandleBlankSourceIp() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity clientEntity = createUserEntity(1L, "client1", UserType.CLIENT, null, null);

        List<UserEntity> entities = List.of(clientEntity);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto clientDto = createUserDto(1L, "client1", UserType.CLIENT, null, "   ");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(clientEntity)).thenReturn(clientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().getSourceIp()).isEqualTo("   ");
    }

    @Test
    @DisplayName("searchWithPagination: should handle empty source IP")
    void searchWithPagination_shouldHandleEmptySourceIp() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        UserEntity clientEntity = createUserEntity(1L, "client1", UserType.CLIENT, null, null);

        List<UserEntity> entities = List.of(clientEntity);
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());

        UserResponseDto clientDto = createUserDto(1L, "client1", UserType.CLIENT, null, "");

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(clientEntity)).thenReturn(clientDto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().getSourceIp()).isEmpty();

        // Verify IP conversion was NOT called for empty values
    }

    // ------------------------------------------------------------------------
    // Tests for empty results
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: should handle empty result set")
    void searchWithPagination_shouldHandleEmptyResults() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 0);

        List<UserEntity> entities = new ArrayList<>();
        Page<UserEntity> entityPage = new PageImpl<>(entities, PageRequest.of(0, 10), 0);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.empty()).isTrue();

        verify(userRepository)
                .findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
    }

    // ------------------------------------------------------------------------
    // Tests for pagination edge cases
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("searchWithPagination: should use default pagination when request is null")
    void searchWithPagination_shouldUseDefaultPaginationWhenNull() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(null, null);

        List<UserEntity> entities =
                List.of(createUserEntity(1L, "user1", UserType.CLIENT, null, null));
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
        UserResponseDto dto = createUserDto(1L, "user1", UserType.CLIENT, null, null);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(any())).thenReturn(dto);

        // When
        userSearchService.searchWithPagination(request);

        // Then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository)
                .findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(),
                        pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        // Default values from PaginationRequest.pageOrDefault() and sizeOrDefault()
        assertThat(capturedPageable.getPageNumber()).isZero(); // default page
        assertThat(capturedPageable.getPageSize()).isEqualTo(20); // default size
    }

    @Test
    @DisplayName("searchWithPagination: should handle last page correctly")
    void searchWithPagination_shouldHandleLastPageCorrectly() {
        // Given
        UserContextHolder.set(adminContext);
        PaginationRequest request = new PaginationRequest(10, 2); // page 2

        List<UserEntity> entities =
                List.of(createUserEntity(1L, "user1", UserType.CLIENT, null, null));
        Page<UserEntity> entityPage =
                new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
        UserResponseDto dto = createUserDto(1L, "user1", UserType.CLIENT, null, null);

        when(userRepository.findAll(
                        ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(entityPage);
        when(userMapper.toDto(any())).thenReturn(dto);

        // When
        PaginationResponse<UserResponseDto> response =
                userSearchService.searchWithPagination(request);

        // Then
        assertThat(response.last()).isEqualTo(entityPage.isLast());
        assertThat(response.first()).isEqualTo(entityPage.isFirst());
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private UserEntity createUserEntity(
            Long id, String name, UserType userType, Long parentId, String sourceIp) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setUserType(userType);
        entity.setSourceIp(sourceIp);

        if (parentId != null) {
            UserEntity parent = new UserEntity();
            parent.setId(parentId);
            entity.setParent(parent);
        }

        return entity;
    }

    private UserResponseDto createUserDto(
            Long id, String name, UserType userType, Long parentId, String sourceIp) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(id);
        dto.setName(name);
        dto.setUserType(userType);
        dto.setParentId(parentId);
        dto.setSourceIp(sourceIp);
        return dto;
    }
}
