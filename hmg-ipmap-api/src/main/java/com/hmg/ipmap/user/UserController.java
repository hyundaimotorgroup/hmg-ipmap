package com.hmg.ipmap.user;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @Operation(
            summary = "Get All users",
            description = "Fetch all users",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                PaginationResponse
                                                                                        .class))))
            })
    @GetMapping
    public PaginationResponse<UserResponseDto> findAll(
            @ParameterObject @Valid @ModelAttribute PaginationRequest req) {

        return service.searchWithPagination(req);
    }

    @Operation(
            summary = "Get user details",
            description = "Get user details",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UserResponseDto.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found",
                        content = @Content)
            })
    @GetMapping("/{id}")
    public UserResponseDto findById(
            @Parameter(description = "user id", example = "1") @Valid @PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(
            summary = "Add a new user",
            description = "Add a new user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UserResponseDto.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflict user",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PostMapping
    public UserResponseDto create(
            @Parameter(description = "User request object") @Valid @RequestBody
                    UserRequestDto source) {
        return service.create(source);
    }

    @Operation(
            summary = "Update a user",
            description = "Update a user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = UserResponseDto.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found",
                        content = @Content),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflict user",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PutMapping("/{id}")
    public UserResponseDto update(
            @Valid @PathVariable Long id, @RequestBody UserRequestDto source) {
        return service.update(id, source);
    }

    @Operation(
            summary = "Delete a user",
            description = "Delete a user from system",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found",
                        content = @Content)
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @PathVariable Long id) {
        service.delete(id);
    }
}
