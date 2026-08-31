package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;

public interface UserService {

    UserEntity findByApiKeyAndParentIsNull(String apiKey);

    UserEntity findByApiKeyAndSourceIp(String apiKey, String sourceIp);

    PaginationResponse<UserResponseDto> searchWithPagination(PaginationRequest req);

    void checkUserAccess(UserContext requester, UserEntity targetUser);

    /**
     * Returns the {@link UserEntity} with the given {@code id}.
     *
     * <p>Intended for internal service-to-service use where a raw entity is required without DTO
     * conversion or access-control checks. Callers are responsible for performing any necessary
     * ownership or scope validation.
     *
     * @param id the user identifier
     * @return the matching {@link UserEntity}
     * @throws com.hmg.ipmap.common.exception.NotFoundException if no user exists with that id
     */
    UserEntity getEntityById(Long id);

    UserResponseDto findById(Long targetUserId);

    UserResponseDto create(UserRequestDto userRequestDto);

    UserResponseDto update(Long id, UserRequestDto userRequestDto);

    void delete(Long id);
}
