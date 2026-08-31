package com.hmg.ipmap.user.dto;

import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.user.UserResponseTemplateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UserRequestDto(
        @Schema(description = "The name of the new user", example = "John Doe")
                @NotBlank(message = "name is required")
                @Size(
                        min = 2,
                        max = 100,
                        message = "name length must be between {min} and {max} characters")
                String name,
        @Size(max = 45, message = "source_ip length must be <= {max} characters")
                @Pattern(
                        regexp =
                                "^((25[0-5]|2[0-4]\\d|1\\d\\d|\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|\\d?\\d)){3})$",
                        message = "source_ip must be a valid IPv4 address (e.g., 127.0.0.1)")
                String sourceIp,
        @Schema(
                        description =
                                "The parent Id of the new user. This is for Sub Client type, must fill the parent id",
                        example = "10",
                        nullable = true)
                @Positive(message = "parent_id must be a positive number")
                Long parentId,
        @Schema(description = "The user type of the new user", example = "SUB_CLIENT")
                @NotNull(message = "user_type is required")
                UserType userType,
        @Schema(
                        description = "The IP Location response template for this user. ",
                        example = "DEFAULT",
                        nullable = true)
                UserResponseTemplateEnum responseTemplate) {}
