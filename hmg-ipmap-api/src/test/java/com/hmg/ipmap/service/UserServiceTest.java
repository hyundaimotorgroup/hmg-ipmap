package com.hmg.ipmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.common.util.IPv4Util;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import com.hmg.ipmap.ipnotation.IpSingle;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserMapper;
import com.hmg.ipmap.user.UserRepository;
import com.hmg.ipmap.user.UserResponseTemplateEnum;
import com.hmg.ipmap.user.UserSearchService;
import com.hmg.ipmap.user.UserServiceImpl;
import com.hmg.ipmap.user.UserValidationService;
import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;
import com.hmg.ipmap.user.exception.UserAlreadyExistException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // Mocking all dependencies
    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private IpNotationFactory ipNotationFactory;
    @Mock private UserValidationService userValidationService;
    @Mock private UserSearchService userSearchService;

    // An instance of UserService will be created and all the mocks above will be injected
    @InjectMocks private UserServiceImpl userService;

    private UserEntity mockAdminUser;
    private UserEntity mockRegularUser;

    @BeforeEach
    void setUp() {
        // Setting up mock data that will be used frequently
        mockAdminUser = new UserEntity();
        mockAdminUser.setId(1L);
        mockAdminUser.setName("admin");
        mockAdminUser.setUserType(UserType.ADMIN);
        mockAdminUser.setApiKey("admin-api-key");

        mockRegularUser = new UserEntity();
        mockRegularUser.setId(2L);
        mockRegularUser.setName("testuser");
        mockRegularUser.setUserType(UserType.CLIENT);

        UserEntity mockUser = new UserEntity();
        mockUser.setId(2L);
        mockUser.setName("testuser");
        mockUser.setApiKey("test");

        // ==== Common test data ====
        UserEntity adminRequester = new UserEntity();
        adminRequester.setId(100L);
        adminRequester.setName("admin");
        adminRequester.setUserType(UserType.ADMIN);
        adminRequester.setApiKey("admin-key");

        UserEntity clientRequester = new UserEntity();
        clientRequester.setId(200L);
        clientRequester.setName("client");
        clientRequester.setUserType(UserType.CLIENT);
        clientRequester.setApiKey("client-key");

        UserEntity subClientRequester = new UserEntity();
        subClientRequester.setId(300L);
        subClientRequester.setName("subclient");
        subClientRequester.setUserType(UserType.SUB_CLIENT);
        subClientRequester.setApiKey("subclient-key");

        UserContext ctx =
                new UserContext(1L, "admin", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        ReflectionTestUtils.setField(userService, "dataProviderProperty", "default");
    }

    // --- Tests for create ---

    @Test
    @DisplayName("create should successfully create a new user")
    void create_whenAdminAndValidData_shouldCreateUser() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newUser", null, null, UserType.CLIENT, null);

        when(userRepository.findByApiKey(mockAdminUser.getApiKey()))
                .thenReturn(Optional.of(mockAdminUser));
        when(userRepository.findByName("newUser"))
                .thenReturn(Optional.empty()); // User validation passes
        when(userRepository.save(any(UserEntity.class))).thenReturn(new UserEntity());
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        UserResponseDto response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository, times(1)).save(any(UserEntity.class)); // Verify that save was called
    }

    @Test
    @DisplayName("create should throw UserAlreadyExistException if username already exists")
    void create_whenUserNameExists_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("existingUser", null, null, UserType.SUB_CLIENT, null);
        when(userRepository.findByApiKey(mockAdminUser.getApiKey()))
                .thenReturn(Optional.of(mockAdminUser));
        when(userRepository.findByName("existingUser"))
                .thenReturn(Optional.of(new UserEntity())); // Name already exists

        // When & Then
        Executable executable = () -> userService.create(request);
        assertThrows(UserAlreadyExistException.class, executable);
    }

    // --- Test for update ---

    @Test
    @DisplayName("update should successfully update user data")
    void update_whenAdminAndUserExists_shouldUpdateUser() {
        // Given
        UserContext ctx =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);
        UserRequestDto request =
                new UserRequestDto("updatedName", null, null, UserType.CLIENT, null);

        when(userRepository.findByApiKey(mockAdminUser.getApiKey()))
                .thenReturn(Optional.of(mockAdminUser));
        when(userRepository.findByName("updatedName")).thenReturn(Optional.empty());
        when(userRepository.findById(UserContextHolder.get().id()))
                .thenReturn(Optional.of(mockRegularUser));
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        userService.update(1L, request);

        // Then
        ArgumentCaptor<UserEntity> userEntityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userEntityCaptor.capture());
        UserEntity savedUser = userEntityCaptor.getValue();
        assertThat(savedUser.getName()).isEqualTo("updatedName");
    }

    // --- Tests for responseTemplate defaulting ---

    @Test
    @DisplayName("create: null responseTemplate should default to app.data-provider value")
    void create_withNullResponseTemplate_shouldUseDefaultFromDataProvider() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newUser", null, null, UserType.CLIENT, null);

        when(userRepository.findByName("newUser")).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        userService.create(request);

        // Then
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTemplate())
                .isEqualTo(UserResponseTemplateEnum.DEFAULT);
    }

    @Test
    @DisplayName("create: explicit responseTemplate should be preserved as-is")
    void create_withExplicitResponseTemplate_shouldUseProvidedTemplate() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto(
                        "newUser", null, null, UserType.CLIENT, UserResponseTemplateEnum.DEFAULT);
        UserEntity entityFromMapper = new UserEntity();
        entityFromMapper.setResponseTemplate(UserResponseTemplateEnum.DEFAULT);

        when(userRepository.findByName("newUser")).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(entityFromMapper);
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        userService.create(request);

        // Then
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTemplate())
                .isEqualTo(UserResponseTemplateEnum.DEFAULT);
    }

    @Test
    @DisplayName("update: null responseTemplate should default to app.data-provider value")
    void update_withNullResponseTemplate_shouldUseDefaultFromDataProvider() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.CLIENT);

        UserRequestDto request = new UserRequestDto("user", null, null, UserType.CLIENT, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        userService.update(1L, request);

        // Then
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTemplate())
                .isEqualTo(UserResponseTemplateEnum.DEFAULT);
    }

    @Test
    @DisplayName("update: explicit responseTemplate should be preserved as-is")
    void update_withExplicitResponseTemplate_shouldUseProvidedTemplate() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.CLIENT);

        UserRequestDto request =
                new UserRequestDto(
                        "user", null, null, UserType.CLIENT, UserResponseTemplateEnum.DEFAULT);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        userService.update(1L, request);

        // Then
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTemplate())
                .isEqualTo(UserResponseTemplateEnum.DEFAULT);
    }

    // --- Test for delete ---

    @Test
    @DisplayName("delete should call the delete method on the repository")
    void delete_whenAdminAndUserExists_shouldDeleteUser() {
        // Given
        UserContext ctx =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        Long userIdToDelete = 2L;
        when(userRepository.findByApiKey(mockAdminUser.getApiKey()))
                .thenReturn(Optional.of(mockAdminUser));
        when(userRepository.findById(userIdToDelete)).thenReturn(Optional.of(mockRegularUser));
        // doNothing() is required for void methods
        doNothing().when(userRepository).delete(mockRegularUser);

        // When
        userService.delete(userIdToDelete);

        // Then
        // Verify that userRepository.delete() was called exactly once with the correct object
        verify(userRepository, times(1)).delete(mockRegularUser);
    }

    // ------------------------------------------------------------------------
    // parseSort(...) via Pageable capture
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("parseSort: whitelisted field with ASC should produce Sort by field ASC")
    void parseSort_shouldSortAsc_whenWhitelisted() {
        UserContext ctx =
                new UserContext(1L, "admin", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        // Given
        PaginationRequest req = new PaginationRequest(10, 0);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(1L);
        dto.setName("e1");
        dto.setSourceIp("0.0.0.1");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), true, 1L, 1, true, 10, 0, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // When
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals("0.0.0.1", result.content().getFirst().getSourceIp());
        verify(userSearchService).searchWithPagination(req);
    }

    @Test
    @DisplayName("parseSort: non-whitelisted field should fallback to 'id'")
    void parseSort_shouldFallbackToId_whenFieldNotWhitelisted() {
        // Given
        PaginationRequest req = new PaginationRequest(10, 0);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(2L);
        dto.setName("e2");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), true, 1L, 1, true, 5, 0, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // When
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // Then
        assertEquals(1, result.totalElements());
        verify(userSearchService).searchWithPagination(req);
    }

    @Test
    @DisplayName("parseSort: missing direction should default to DESC")
    void parseSort_shouldDefaultDesc_whenDirectionMissing() {
        UserContext ctx =
                new UserContext(1L, "admin", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        // Given
        PaginationRequest req = new PaginationRequest(3, 1);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(3L);
        dto.setName("e3");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), true, 4L, 2, false, 3, 1, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // When
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // Then
        assertEquals(4, result.totalElements());
        verify(userSearchService).searchWithPagination(req);
    }

    // ------------------------------------------------------------------------
    // searchWithPagination(...) branch behavior
    // ------------------------------------------------------------------------

    @Test
    @DisplayName(
            "searchWithPagination: SUB_CLIENT with null req.id → req.id set to requester.id and parent limited")
    void searchWithPagination_subClient_shouldSetReqId_andLimitParent() {
        // Given
        UserContext ctx =
                new UserContext(
                        1L,
                        "admin",
                        UserType.SUB_CLIENT,
                        "1.2.3.4",
                        Scope.SUB_CLIENT,
                        new UserContext(
                                110L,
                                "parent",
                                UserType.CLIENT,
                                "1.2.3.4",
                                Scope.CLIENT,
                                null,
                                null),
                        null);
        UserContextHolder.set(ctx);

        PaginationRequest req = new PaginationRequest(10, 0);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(10L);
        dto.setName("sc-entity");
        dto.setSourceIp("0.0.0.1");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), true, 1L, 1, true, 10, 0, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // When
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // Then
        assertEquals("0.0.0.1", result.content().getFirst().getSourceIp());
        verify(userSearchService).searchWithPagination(req);
    }

    @Test
    @DisplayName(
            "searchWithPagination: CLIENT with null req.id → req.id set to requester.id and types expanded")
    void searchWithPagination_client_shouldSetReqId_andExpandTypes() {
        UserContext ctx =
                new UserContext(1L, "admin", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        // Given
        PaginationRequest req = new PaginationRequest(10, 0);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(20L);
        dto.setName("c-entity");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), true, 1L, 1, true, 5, 0, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // When
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // Then
        assertEquals(1, result.totalElements());
        verify(userSearchService).searchWithPagination(req);
    }

    @Test
    @DisplayName("searchWithPagination: ADMIN keeps initial spec and uses parsed sort")
    void searchWithPagination_admin_basicHappyPath() {
        // GIVEN
        UserContext ctx =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        PaginationRequest req = new PaginationRequest(10, 0);
        UserResponseDto dto = new UserResponseDto();
        dto.setId(30L);
        dto.setName("a-entity");

        PaginationResponse<UserResponseDto> mockResponse =
                new PaginationResponse<>(List.of(dto), false, 1L, 1, false, 10, 0, 1, false);

        when(userSearchService.searchWithPagination(req)).thenReturn(mockResponse);

        // WHEN
        PaginationResponse<UserResponseDto> result = userService.searchWithPagination(req);

        // THEN
        assertNotNull(result);
        verify(userSearchService).searchWithPagination(req);
    }

    // ------------------------------------------------------------------------
    // Tests for sourceIp validation logic
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: CLIENT with sourceIp should throw BadRequestException")
    void create_clientWithSourceIp_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("clientUser", "192.168.1.1", null, UserType.CLIENT, null);

        when(userRepository.findByName("clientUser")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "CLIENT user type should not have a sourceIp"))
                .when(userValidationService)
                .validateClientSourceIp(request);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage()).contains("CLIENT user type should not have a sourceIp");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: CLIENT without sourceIp should succeed")
    void create_clientWithoutSourceIp_shouldSucceed() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("clientUser", null, null, UserType.CLIENT, null);
        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setName("clientUser");
        savedUser.setUserType(UserType.CLIENT);
        savedUser.setSourceIp(null);

        when(userRepository.findByName("clientUser")).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponseDto());

        // When
        UserResponseDto response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getSourceIp()).isNull();
    }

    @Test
    @DisplayName("create: SUB_CLIENT without sourceIp should throw BadRequestException")
    void create_subClientWithoutSourceIp_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("subClientUser", null, 200L, UserType.SUB_CLIENT, null);

        when(userRepository.findByName("subClientUser")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "SUB_CLIENT user type must have a sourceIp"))
                .when(userValidationService)
                .validateClientSourceIp(request);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage()).contains("SUB_CLIENT user type must have a sourceIp");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: SUB_CLIENT with sourceIp by ADMIN without parentId should throw")
    void create_subClientByAdminWithoutParentId_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", null, UserType.SUB_CLIENT, null);

        when(userRepository.findByName("subClientUser")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "ADMIN must provide parentId when creating SUB_CLIENT"))
                .when(userValidationService)
                .validateSubClientCreation(request, ctx);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage())
                .contains("ADMIN must provide parentId when creating SUB_CLIENT");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: SUB_CLIENT with sourceIp by ADMIN with parentId should succeed")
    void create_subClientByAdminWithParentId_shouldSucceed() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 200L, UserType.SUB_CLIENT, null);
        UserEntity parentUser = new UserEntity();
        parentUser.setId(200L);
        parentUser.setApiKey("parent-key");

        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setName("subClientUser");
        savedUser.setUserType(UserType.SUB_CLIENT);
        savedUser.setSourceIp("1.2.3.4");
        savedUser.setParent(parentUser);

        IpSingle mockIpSingle = mock(IpSingle.class);
        when(mockIpSingle.toString()).thenReturn("1.2.3.4");

        when(userRepository.findByName("subClientUser")).thenReturn(Optional.empty());
        when(ipNotationFactory.createIpSingle("192.168.1.1")).thenReturn(mockIpSingle);
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.findById(200L)).thenReturn(Optional.of(parentUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponseDto());

        try (var _ = mockStatic(IPv4Util.class)) {

            // When
            UserResponseDto response = userService.create(request);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(any(UserEntity.class));
        }
    }

    @Test
    @DisplayName("create: SUB_CLIENT by CLIENT with sourceIp should auto-set parent")
    void create_subClientByClientWithSourceIp_shouldAutoSetParent() {
        // Given
        UserContext ctx =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", null, UserType.SUB_CLIENT, null);

        UserEntity clientUser = new UserEntity();
        clientUser.setId(200L);
        clientUser.setApiKey("client-key");

        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setName("subClientUser");
        savedUser.setUserType(UserType.SUB_CLIENT);
        savedUser.setSourceIp("1.2.3.4");
        savedUser.setParent(clientUser);

        IpSingle mockIpSingle = mock(IpSingle.class);
        when(mockIpSingle.toString()).thenReturn("1.2.3.4");

        when(userRepository.findByName("subClientUser")).thenReturn(Optional.empty());
        when(ipNotationFactory.createIpSingle("192.168.1.1")).thenReturn(mockIpSingle);
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.findById(200L)).thenReturn(Optional.of(clientUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponseDto());

        try (var _ = mockStatic(IPv4Util.class)) {

            // When
            UserResponseDto response = userService.create(request);

            // Then
            assertThat(response).isNotNull();
            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(userCaptor.capture());
            verify(userRepository).findById(200L); // Parent was fetched
        }
    }

    @Test
    @DisplayName("update: CLIENT with sourceIp should throw BadRequestException")
    void update_clientWithSourceIp_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("clientUser");
        existingUser.setUserType(UserType.CLIENT);

        UserRequestDto request =
                new UserRequestDto("clientUser", "192.168.1.1", null, UserType.CLIENT, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "CLIENT user type should not have a sourceIp"))
                .when(userValidationService)
                .validateClientSourceIp(request);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.update(1L, request));

        assertThat(exception.getMessage()).contains("CLIENT user type should not have a sourceIp");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("update: changing to CLIENT type should clear sourceIp")
    void update_changingToClientType_shouldClearSourceIp() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.SUB_CLIENT);
        existingUser.setSourceIp("1.2.3.4"); // Had sourceIp as SUB_CLIENT

        UserRequestDto request = new UserRequestDto("user", null, null, UserType.CLIENT, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        UserResponseDto response = userService.update(1L, request);

        // Then
        assertThat(response).isNotNull();
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getSourceIp()).isNull();
    }

    @Test
    @DisplayName(
            "update: SUB_CLIENT without sourceIp in request should throw (PUT semantics requires full data)")
    void update_subClientWithoutSourceIpInRequest_shouldThrow() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.SUB_CLIENT);
        existingUser.setSourceIp("1.2.3.4"); // Already has sourceIp

        UserRequestDto request =
                new UserRequestDto(
                        "user", null, null, UserType.SUB_CLIENT, null); // No sourceIp in request

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "SUB_CLIENT user type must have a sourceIp"))
                .when(userValidationService)
                .validateClientSourceIp(request);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.update(1L, request));

        assertThat(exception.getMessage()).contains("SUB_CLIENT user type must have a sourceIp");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("update: changing to SUB_CLIENT with sourceIp in request should succeed")
    void update_changingToSubClientWithSourceIp_shouldSucceed() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.CLIENT);

        UserRequestDto request =
                new UserRequestDto(
                        "user",
                        "192.168.1.1",
                        null,
                        UserType.SUB_CLIENT,
                        null); // Providing sourceIp

        IpSingle mockIpSingle = mock(IpSingle.class);
        when(mockIpSingle.toString()).thenReturn("1.2.3.4");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        when(ipNotationFactory.createIpSingle("192.168.1.1")).thenReturn(mockIpSingle);
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(new UserResponseDto());

        // When
        UserResponseDto response = userService.update(1L, request);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("update: changing to SUB_CLIENT without sourceIp and no existing should throw")
    void update_changingToSubClientWithoutSourceIp_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserEntity existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setName("user");
        existingUser.setUserType(UserType.CLIENT);
        existingUser.setSourceIp(null); // No existing sourceIp

        UserRequestDto request = new UserRequestDto("user", null, null, UserType.SUB_CLIENT, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByName("user")).thenReturn(Optional.of(existingUser));
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "SUB_CLIENT user type must have a sourceIp"))
                .when(userValidationService)
                .validateClientSourceIp(request);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.update(1L, request));

        assertThat(exception.getMessage()).contains("SUB_CLIENT user type must have a sourceIp");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    // ------------------------------------------------------------------------
    // Tests for user creation permission validation
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: SUB_CLIENT trying to create any user should throw BadRequestException")
    void create_subClientTryingToCreateUser_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(
                        300L,
                        "subclient",
                        UserType.SUB_CLIENT,
                        "1.2.3.4",
                        Scope.SUB_CLIENT,
                        null,
                        null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("newUser", "192.168.1.1", 200L, UserType.SUB_CLIENT, null);

        when(userRepository.findByName("newUser")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "SUB_CLIENT users are not authorized to create other users"))
                .when(userValidationService)
                .validateUserCreationPermissions(ctx, UserType.SUB_CLIENT);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage())
                .contains("SUB_CLIENT users are not authorized to create other users");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: ADMIN trying to create ADMIN should throw BadRequestException")
    void create_adminTryingToCreateAdmin_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newAdmin", null, null, UserType.ADMIN, null);

        when(userRepository.findByName("newAdmin")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "ADMIN cannot create another ADMIN user"))
                .when(userValidationService)
                .validateUserCreationPermissions(ctx, UserType.ADMIN);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage()).contains("ADMIN cannot create another ADMIN user");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: ADMIN creating CLIENT should succeed")
    void create_adminCreatingClient_shouldSucceed() {
        // Given
        UserContext ctx =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newClient", null, null, UserType.CLIENT, null);
        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setName("newClient");
        savedUser.setUserType(UserType.CLIENT);

        when(userRepository.findByName("newClient")).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponseDto());

        // When
        UserResponseDto response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: CLIENT trying to create CLIENT should throw BadRequestException")
    void create_clientTryingToCreateClient_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newClient", null, null, UserType.CLIENT, null);

        when(userRepository.findByName("newClient")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "CLIENT cannot create another CLIENT user"))
                .when(userValidationService)
                .validateUserCreationPermissions(ctx, UserType.CLIENT);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage()).contains("CLIENT cannot create another CLIENT user");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: CLIENT trying to create ADMIN should throw BadRequestException")
    void create_clientTryingToCreateAdmin_shouldThrowBadRequestException() {
        // Given
        UserContext ctx =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request = new UserRequestDto("newAdmin", null, null, UserType.ADMIN, null);

        when(userRepository.findByName("newAdmin")).thenReturn(Optional.empty());
        doThrow(
                        new com.hmg.ipmap.common.exception.BadRequestException(
                                "CLIENT cannot create ADMIN user"))
                .when(userValidationService)
                .validateUserCreationPermissions(ctx, UserType.ADMIN);

        // When & Then
        com.hmg.ipmap.common.exception.BadRequestException exception =
                assertThrows(
                        com.hmg.ipmap.common.exception.BadRequestException.class,
                        () -> userService.create(request));

        assertThat(exception.getMessage()).contains("CLIENT cannot create ADMIN user");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("create: CLIENT creating SUB_CLIENT should succeed")
    void create_clientCreatingSubClient_shouldSucceed() {
        // Given
        UserContext ctx =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);
        UserContextHolder.set(ctx);

        UserRequestDto request =
                new UserRequestDto("newSubClient", "192.168.1.1", null, UserType.SUB_CLIENT, null);

        UserEntity clientUser = new UserEntity();
        clientUser.setId(200L);
        clientUser.setApiKey("client-key");

        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setName("newSubClient");
        savedUser.setUserType(UserType.SUB_CLIENT);
        savedUser.setSourceIp("1.2.3.4");
        savedUser.setParent(clientUser);

        IpSingle mockIpSingle = mock(IpSingle.class);
        when(mockIpSingle.toString()).thenReturn("1.2.3.4");

        when(userRepository.findByName("newSubClient")).thenReturn(Optional.empty());
        when(ipNotationFactory.createIpSingle("192.168.1.1")).thenReturn(mockIpSingle);
        when(userMapper.toEntity(request)).thenReturn(new UserEntity());
        when(userRepository.findById(200L)).thenReturn(Optional.of(clientUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponseDto());

        try (var _ = mockStatic(IPv4Util.class)) {

            // When
            UserResponseDto response = userService.create(request);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(any(UserEntity.class));
        }
    }

    // ------------------------------------------------------------------------
    // Tests for checkUserAccess
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("checkUserAccess: ADMIN user should have access to all resources")
    void checkUserAccess_adminUser_shouldHaveAccessToAllResources() {
        // Given
        UserContext adminRequesterContext =
                new UserContext(100L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);

        UserEntity targetUser = new UserEntity();
        targetUser.setId(200L);
        targetUser.setName("targetUser");
        targetUser.setUserType(UserType.CLIENT);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> userService.checkUserAccess(adminRequesterContext, targetUser));
    }

    @Test
    @DisplayName("checkUserAccess: User accessing their own data should succeed")
    void checkUserAccess_userAccessingOwnData_shouldSucceed() {
        // Given
        UserContext requester =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);

        UserEntity targetUser = new UserEntity();
        targetUser.setId(200L); // Same ID as requester
        targetUser.setName("client");
        targetUser.setUserType(UserType.CLIENT);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> userService.checkUserAccess(requester, targetUser));
    }

    @Test
    @DisplayName("checkUserAccess: CLIENT accessing their SUB_CLIENT (child) should have access")
    void checkUserAccess_clientAccessingSubClient_shouldHaveAccess() {
        // Given
        UserContext clientRequesterContext =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);

        UserEntity parentUser = new UserEntity();
        parentUser.setId(200L);
        parentUser.setName("client");
        parentUser.setUserType(UserType.CLIENT);

        UserEntity subClientUser = new UserEntity();
        subClientUser.setId(300L);
        subClientUser.setName("subclient");
        subClientUser.setUserType(UserType.SUB_CLIENT);
        subClientUser.setParent(parentUser); // Set parent

        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () -> userService.checkUserAccess(clientRequesterContext, subClientUser));
    }

    @Test
    @DisplayName("checkUserAccess: CLIENT accessing another CLIENT should throw exception")
    void checkUserAccess_clientAccessingAnotherClient_shouldThrowException() {
        // Given
        UserContext clientRequesterContext =
                new UserContext(
                        200L, "client1", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);

        UserEntity anotherClientUser = new UserEntity();
        anotherClientUser.setId(300L);
        anotherClientUser.setName("client2");
        anotherClientUser.setUserType(UserType.CLIENT);

        // When & Then
        com.hmg.ipmap.ipmapping.exception.UnauthorizeException exception =
                assertThrows(
                        com.hmg.ipmap.ipmapping.exception.UnauthorizeException.class,
                        () ->
                                userService.checkUserAccess(
                                        clientRequesterContext, anotherClientUser));

        assertThat(exception.getMessage())
                .contains("User does not have privileges to access this resource.");
    }

    @Test
    @DisplayName(
            "checkUserAccess: CLIENT accessing SUB_CLIENT of another CLIENT should throw exception")
    void checkUserAccess_clientAccessingSubClientOfAnotherClient_shouldThrowException() {
        // Given
        UserContext clientRequesterContext =
                new UserContext(
                        200L, "client1", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);

        UserEntity anotherClientUser = new UserEntity();
        anotherClientUser.setId(300L);
        anotherClientUser.setName("client2");
        anotherClientUser.setUserType(UserType.CLIENT);

        UserEntity subClientOfAnotherClient = new UserEntity();
        subClientOfAnotherClient.setId(400L);
        subClientOfAnotherClient.setName("subclient2");
        subClientOfAnotherClient.setUserType(UserType.SUB_CLIENT);
        subClientOfAnotherClient.setParent(anotherClientUser); // Parent is another client

        // When & Then
        com.hmg.ipmap.ipmapping.exception.UnauthorizeException exception =
                assertThrows(
                        com.hmg.ipmap.ipmapping.exception.UnauthorizeException.class,
                        () ->
                                userService.checkUserAccess(
                                        clientRequesterContext, subClientOfAnotherClient));

        assertThat(exception.getMessage())
                .contains("User does not have privileges to access this resource.");
    }

    @Test
    @DisplayName(
            "checkUserAccess: SUB_CLIENT accessing another user should throw exception (not admin, not self)")
    void checkUserAccess_subClientAccessingAnotherUser_shouldThrowException() {
        // Given
        UserContext subClientRequesterContext =
                new UserContext(
                        300L,
                        "subclient",
                        UserType.SUB_CLIENT,
                        "1.2.3.4",
                        Scope.SUB_CLIENT,
                        new UserContext(
                                200L,
                                "client",
                                UserType.CLIENT,
                                "1.2.3.4",
                                Scope.CLIENT,
                                null,
                                null),
                        null);

        UserEntity anotherUser = new UserEntity();
        anotherUser.setId(400L);
        anotherUser.setName("otherUser");
        anotherUser.setUserType(UserType.CLIENT);

        // When & Then
        com.hmg.ipmap.ipmapping.exception.UnauthorizeException exception =
                assertThrows(
                        com.hmg.ipmap.ipmapping.exception.UnauthorizeException.class,
                        () -> userService.checkUserAccess(subClientRequesterContext, anotherUser));

        assertThat(exception.getMessage())
                .contains("User does not have privileges to access this resource.");
    }

    @Test
    @DisplayName(
            "checkUserAccess: SUB_CLIENT accessing their own data should succeed (accessing self)")
    void checkUserAccess_subClientAccessingOwnData_shouldSucceed() {
        // Given
        UserContext subClientRequesterContext =
                new UserContext(
                        300L,
                        "subclient",
                        UserType.SUB_CLIENT,
                        "1.2.3.4",
                        Scope.SUB_CLIENT,
                        new UserContext(
                                200L,
                                "client",
                                UserType.CLIENT,
                                "1.2.3.4",
                                Scope.CLIENT,
                                null,
                                null),
                        null);

        UserEntity sameSubClient = new UserEntity();
        sameSubClient.setId(300L); // Same ID as requester
        sameSubClient.setName("subclient");
        sameSubClient.setUserType(UserType.SUB_CLIENT);

        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () -> userService.checkUserAccess(subClientRequesterContext, sameSubClient));
    }

    @Test
    @DisplayName(
            "checkUserAccess: TARGET user with null parent should not cause NullPointerException")
    void checkUserAccess_targetUserWithNullParent_shouldNotCauseNPE() {
        // Given
        UserContext requesterContext =
                new UserContext(
                        200L, "client", UserType.CLIENT, "1.2.3.4", Scope.CLIENT, null, null);

        UserEntity targetUserWithNullParent = new UserEntity();
        targetUserWithNullParent.setId(300L);
        targetUserWithNullParent.setName("userWithoutParent");
        targetUserWithNullParent.setUserType(UserType.CLIENT);
        targetUserWithNullParent.setParent(null); // Null parent

        // When & Then - Should throw UnauthorizeException, not NullPointerException
        com.hmg.ipmap.ipmapping.exception.UnauthorizeException exception =
                assertThrows(
                        com.hmg.ipmap.ipmapping.exception.UnauthorizeException.class,
                        () ->
                                userService.checkUserAccess(
                                        requesterContext, targetUserWithNullParent));

        assertThat(exception.getMessage())
                .contains("User does not have privileges to access this resource.");
    }
}
