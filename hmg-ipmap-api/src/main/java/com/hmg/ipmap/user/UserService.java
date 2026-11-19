package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;

public interface UserService {

    /**
     * @deprecated Retained for legacy parent-based authentication; scheduled for removal when the
     *     parent/source-IP model is retired.
     */
    @Deprecated(forRemoval = true)
    UserEntity findByApiKeyAndParentIsNull(String apiKey);

    /**
     * @deprecated Retained for legacy source-IP authentication; scheduled for removal when the
     *     parent/source-IP model is retired.
     */
    @Deprecated(forRemoval = true)
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
