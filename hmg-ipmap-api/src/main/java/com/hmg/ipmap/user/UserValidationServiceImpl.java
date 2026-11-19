package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.user.dto.UserRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service class responsible for validating user-related business rules and constraints.
 *
 * <p>This service restricts provisioning to CLIENT accounts and validates source IP rules for
 * current and persisted legacy users, including:
 *
 * <ul>
 *   <li>User type-based creation permissions
 *   <li>Source IP address validation based on user type
 *   <li>Legacy SUB_CLIENT validation retained until support is removed
 * </ul>
 *
 * @see UserServiceImpl
 * @see UserType
 */
@Service
@RequiredArgsConstructor
public class UserValidationServiceImpl implements UserValidationService {

    @Override
    public void validateUserCreationPermissions(UserContext requester, UserType targetUserType) {
        if (targetUserType == UserType.SUB_CLIENT) {
            throw new BadRequestException(
                    "SUB_CLIENT user type is deprecated and cannot be created.");
        }

        if (targetUserType != UserType.CLIENT) {
            throw new BadRequestException("Only CLIENT users can be created.");
        }
    }

    @Override
    public void validateClientSourceIp(UserRequestDto userRequestDto) {
        if (userRequestDto.userType() == UserType.CLIENT
                && StringUtils.hasText(userRequestDto.sourceIp())) {
            throw new BadRequestException("CLIENT user type should not have a sourceIp.");
        }
    }

    /**
     * @deprecated Scheduled for removal with SUB_CLIENT support; provisioning rejects this type.
     */
    @Deprecated(forRemoval = true)
    @Override
    public void validateSubClientCreation(UserRequestDto userRequestDto, UserContext requester) {
        // SUB_CLIENT must always have a sourceIp
        if (userRequestDto.sourceIp() == null || userRequestDto.sourceIp().isEmpty()) {
            throw new BadRequestException("SUB_CLIENT user type must have a sourceIp");
        }

        if (requester.userType() == UserType.ADMIN && userRequestDto.parentId() == null) {
            throw new BadRequestException("ADMIN must provide parentId when creating SUB_CLIENT");
        }

        if (requester.userType() == UserType.CLIENT
                && userRequestDto.parentId() != null
                && userRequestDto.parentId().longValue() == requester.id()) {
            throw new BadRequestException(
                    "CLIENT cannot specify parentId when creating SUB_CLIENT. Parent will be"
                            + " set automatically to the CLIENT itself.");
        }
    }

    /**
     * @deprecated Scheduled for removal with SUB_CLIENT support; retained for persisted users.
     */
    @Deprecated(forRemoval = true)
    @Override
    public void validateSubClientUpdate(UserRequestDto userRequestDto) {
        if (userRequestDto.userType() == UserType.SUB_CLIENT
                && !StringUtils.hasText(userRequestDto.sourceIp())) {
            throw new BadRequestException("SUB_CLIENT user type must have a sourceIp");
        }
    }
}
