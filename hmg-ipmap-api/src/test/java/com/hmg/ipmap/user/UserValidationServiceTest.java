package com.hmg.ipmap.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.user.dto.UserRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class UserValidationServiceTest {

    @InjectMocks private UserValidationServiceImpl validationService;

    private UserContext adminContext;
    private UserContext clientContext;
    private UserContext subClientContext;

    @BeforeEach
    void setUp() {
        MDC.put("username", "admin");
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

    // ------------------------------------------------------------------------
    // Tests for validateUserCreationPermissions
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("validateUserCreationPermissions: ADMIN creating CLIENT should succeed")
    void validateUserCreationPermissions_adminCreatingClient_shouldSucceed() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () ->
                        validationService.validateUserCreationPermissions(
                                adminContext, UserType.CLIENT));
    }

    @Test
    @DisplayName("validateUserCreationPermissions: ADMIN creating SUB_CLIENT should succeed")
    void validateUserCreationPermissions_adminCreatingSubClient_shouldSucceed() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () ->
                        validationService.validateUserCreationPermissions(
                                adminContext, UserType.SUB_CLIENT));
    }

    @Test
    @DisplayName("validateUserCreationPermissions: ADMIN creating ADMIN should throw exception")
    void validateUserCreationPermissions_adminCreatingAdmin_shouldThrowException() {
        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                validationService.validateUserCreationPermissions(
                                        adminContext, UserType.ADMIN));

        assertThat(exception.getMessage()).isEqualTo("ADMIN user is not allowed to be created");
    }

    @Test
    @DisplayName("validateUserCreationPermissions: CLIENT creating SUB_CLIENT should succeed")
    void validateUserCreationPermissions_clientCreatingSubClient_shouldSucceed() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () ->
                        validationService.validateUserCreationPermissions(
                                clientContext, UserType.SUB_CLIENT));
    }

    @Test
    @DisplayName("validateUserCreationPermissions: CLIENT creating CLIENT should throw exception")
    void validateUserCreationPermissions_clientCreatingClient_shouldThrowException() {
        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                validationService.validateUserCreationPermissions(
                                        clientContext, UserType.CLIENT));

        assertThat(exception.getMessage()).isEqualTo("CLIENT cannot create another CLIENT user.");
    }

    @Test
    @DisplayName("validateUserCreationPermissions: CLIENT creating ADMIN should throw exception")
    void validateUserCreationPermissions_clientCreatingAdmin_shouldThrowException() {
        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                validationService.validateUserCreationPermissions(
                                        clientContext, UserType.ADMIN));

        assertThat(exception.getMessage()).isEqualTo("ADMIN user is not allowed to be created");
    }

    @Test
    @DisplayName(
            "validateUserCreationPermissions: SUB_CLIENT creating any user should throw exception")
    void validateUserCreationPermissions_subClientCreatingUser_shouldThrowException() {
        // When & Then - Try creating CLIENT
        BadRequestException exceptionClient =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                validationService.validateUserCreationPermissions(
                                        subClientContext, UserType.CLIENT));

        assertThat(exceptionClient.getMessage())
                .isEqualTo("SUB_CLIENT user is not authorized to create other users");

        // When & Then - Try creating SUB_CLIENT
        BadRequestException exceptionSubClient =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                validationService.validateUserCreationPermissions(
                                        subClientContext, UserType.SUB_CLIENT));

        assertThat(exceptionSubClient.getMessage())
                .isEqualTo("SUB_CLIENT user is not authorized to create other users");
    }

    // ------------------------------------------------------------------------
    // Tests for validateClientSourceIp
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("validateClientSourceIp: CLIENT with sourceIp should throw exception")
    void validateClientSourceIp_clientWithSourceIp_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("clientUser", "192.168.1.1", null, UserType.CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateClientSourceIp(request));

        assertThat(exception.getMessage())
                .isEqualTo("CLIENT user type should not have a sourceIp.");
    }

    @Test
    @DisplayName("validateClientSourceIp: CLIENT without sourceIp should succeed")
    void validateClientSourceIp_clientWithoutSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("clientUser", null, null, UserType.CLIENT, null);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> validationService.validateClientSourceIp(request));
    }

    @Test
    @DisplayName("validateClientSourceIp: SUB_CLIENT with sourceIp should succeed")
    void validateClientSourceIp_subClientWithSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 2L, UserType.SUB_CLIENT, null);

        // When & Then - Should not throw exception (this method only validates CLIENT)
        assertDoesNotThrow(() -> validationService.validateClientSourceIp(request));
    }

    @Test
    @DisplayName("validateClientSourceIp: ADMIN with sourceIp should succeed")
    void validateClientSourceIp_adminWithSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("adminUser", "192.168.1.1", null, UserType.ADMIN, null);

        // When & Then - Should not throw exception (this method only validates CLIENT)
        assertDoesNotThrow(() -> validationService.validateClientSourceIp(request));
    }

    // ------------------------------------------------------------------------
    // Tests for validateSubClientCreation
    // ------------------------------------------------------------------------

    @Test
    @DisplayName(
            "validateSubClientCreation: SUB_CLIENT with sourceIp and parentId by ADMIN should"
                    + " succeed")
    void validateSubClientCreation_withSourceIpAndParentIdByAdmin_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 2L, UserType.SUB_CLIENT, null);

        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () -> validationService.validateSubClientCreation(request, adminContext));
    }

    @Test
    @DisplayName("validateSubClientCreation: SUB_CLIENT without sourceIp should throw exception")
    void validateSubClientCreation_withoutSourceIp_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", null, 2L, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientCreation(request, adminContext));

        assertThat(exception.getMessage()).isEqualTo("SUB_CLIENT user type must have a sourceIp");
    }

    @Test
    @DisplayName("validateSubClientCreation: SUB_CLIENT with empty sourceIp should throw exception")
    void validateSubClientCreation_withEmptySourceIp_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "", 2L, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientCreation(request, adminContext));

        assertThat(exception.getMessage()).isEqualTo("SUB_CLIENT user type must have a sourceIp");
    }

    @Test
    @DisplayName(
            "validateSubClientCreation: ADMIN creating SUB_CLIENT without parentId should throw"
                    + " exception")
    void validateSubClientCreation_adminWithoutParentId_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", null, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientCreation(request, adminContext));

        assertThat(exception.getMessage())
                .isEqualTo("ADMIN must provide parentId when creating SUB_CLIENT");
    }

    @Test
    @DisplayName(
            "validateSubClientCreation: CLIENT creating SUB_CLIENT with sourceIp should succeed")
    void validateSubClientCreation_clientWithSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", null, UserType.SUB_CLIENT, null);

        // When & Then - Should not throw exception
        assertDoesNotThrow(
                () -> validationService.validateSubClientCreation(request, clientContext));
    }

    @Test
    @DisplayName(
            "validateSubClientCreation: CLIENT creating SUB_CLIENT with own ID as parentId should"
                    + " throw exception")
    void validateSubClientCreation_clientWithOwnIdAsParentId_shouldThrowException() {
        // Given - parentId is same as clientContext.id() (2L)
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 2L, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientCreation(request, clientContext));

        assertThat(exception.getMessage())
                .contains("CLIENT cannot specify parentId when creating SUB_CLIENT");
        assertThat(exception.getMessage()).contains("Parent will be set automatically");
    }

    @Test
    @DisplayName(
            "validateSubClientCreation: CLIENT creating SUB_CLIENT with different parentId should"
                    + " succeed")
    void validateSubClientCreation_clientWithDifferentParentId_shouldSucceed() {
        // Given - parentId is different from clientContext.id() (2L)
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 999L, UserType.SUB_CLIENT, null);

        // When & Then - Should not throw exception (parent is different, so CLIENT might be
        // reassigning)
        assertDoesNotThrow(
                () -> validationService.validateSubClientCreation(request, clientContext));
    }

    // ------------------------------------------------------------------------
    // Tests for validateSubClientUpdate
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("validateSubClientUpdate: SUB_CLIENT with sourceIp should succeed")
    void validateSubClientUpdate_subClientWithSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "192.168.1.1", 2L, UserType.SUB_CLIENT, null);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> validationService.validateSubClientUpdate(request));
    }

    @Test
    @DisplayName("validateSubClientUpdate: SUB_CLIENT without sourceIp should throw exception")
    void validateSubClientUpdate_subClientWithoutSourceIp_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", null, 2L, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientUpdate(request));

        assertThat(exception.getMessage()).isEqualTo("SUB_CLIENT user type must have a sourceIp");
    }

    @Test
    @DisplayName("validateSubClientUpdate: SUB_CLIENT with blank sourceIp should throw exception")
    void validateSubClientUpdate_subClientWithBlankSourceIp_shouldThrowException() {
        // Given
        UserRequestDto request =
                new UserRequestDto("subClientUser", "   ", 2L, UserType.SUB_CLIENT, null);

        // When & Then
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> validationService.validateSubClientUpdate(request));

        assertThat(exception.getMessage()).isEqualTo("SUB_CLIENT user type must have a sourceIp");
    }

    @Test
    @DisplayName("validateSubClientUpdate: CLIENT without sourceIp should succeed")
    void validateSubClientUpdate_clientWithoutSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request =
                new UserRequestDto("clientUser", null, null, UserType.CLIENT, null);

        // When & Then - Should not throw exception (this method only validates SUB_CLIENT)
        assertDoesNotThrow(() -> validationService.validateSubClientUpdate(request));
    }

    @Test
    @DisplayName("validateSubClientUpdate: ADMIN without sourceIp should succeed")
    void validateSubClientUpdate_adminWithoutSourceIp_shouldSucceed() {
        // Given
        UserRequestDto request = new UserRequestDto("adminUser", null, null, UserType.ADMIN, null);

        // When & Then - Should not throw exception (this method only validates SUB_CLIENT)
        assertDoesNotThrow(() -> validationService.validateSubClientUpdate(request));
    }
}
