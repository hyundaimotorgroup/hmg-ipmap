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
 * <p>This service enforces hierarchical access control and validation rules for user creation and
 * updates, including:
 *
 * <ul>
 *   <li>User type-based creation permissions
 *   <li>Source IP address validation based on user type
 *   <li>Parent-child relationship validation for SUB_CLIENT users
 * </ul>
 *
 * <p>User hierarchy:
 *
 * <pre>
 * ADMIN (top-level)
 *   └─ CLIENT (can create SUB_CLIENT)
 *       └─ SUB_CLIENT (cannot create users)
 * </pre>
 *
 * @see UserServiceImpl
 * @see UserType
 */
@Service
@RequiredArgsConstructor
public class UserValidationServiceImpl implements UserValidationService {

    @Override
    public void validateUserCreationPermissions(UserContext requester, UserType targetUserType) {
        // No one can create ADMIN
        if (targetUserType == UserType.ADMIN) {
            throw new BadRequestException("ADMIN user is not allowed to be created");
        }

        // SUB_CLIENT cannot create any user
        if (requester.userType() == UserType.SUB_CLIENT) {
            throw new BadRequestException(
                    "SUB_CLIENT user is not authorized to create other users");
        }

        // CLIENT can only create SUB_CLIENT
        if (requester.userType() == UserType.CLIENT && targetUserType == UserType.CLIENT) {
            throw new BadRequestException("CLIENT cannot create another CLIENT user.");
        }
    }

    @Override
    public void validateClientSourceIp(UserRequestDto userRequestDto) {
        if (userRequestDto.userType() == UserType.CLIENT
                && StringUtils.hasText(userRequestDto.sourceIp())) {
            throw new BadRequestException("CLIENT user type should not have a sourceIp.");
        }
    }

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

    @Override
    public void validateSubClientUpdate(UserRequestDto userRequestDto) {
        if (userRequestDto.userType() == UserType.SUB_CLIENT
                && !StringUtils.hasText(userRequestDto.sourceIp())) {
            throw new BadRequestException("SUB_CLIENT user type must have a sourceIp");
        }
    }
}
