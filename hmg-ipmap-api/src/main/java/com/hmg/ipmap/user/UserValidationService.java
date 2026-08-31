package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.user.dto.UserRequestDto;

public interface UserValidationService {
    /**
     * Validates if the requester has permission to create a user of the specified type.
     *
     * <p>Validation rules:
     *
     * <ul>
     *   <li>No user can create ADMIN type users
     *   <li>SUB_CLIENT users cannot create any users
     *   <li>CLIENT users can only create SUB_CLIENT users
     *   <li>ADMIN users can create CLIENT and SUB_CLIENT users
     * </ul>
     *
     * @param requester the user context of the requester attempting to create a user
     * @param targetUserType the type of user being created
     * @throws BadRequestException if the requester does not have permission to create the target
     *     user type
     */
    void validateUserCreationPermissions(UserContext requester, UserType targetUserType);

    /**
     * Validates that CLIENT users do not have a source IP address.
     *
     * <p>Business rule: Only SUB_CLIENT users should have a source IP address for access control.
     * CLIENT users authenticate via API key only.
     *
     * @param userRequestDto the user request data containing user type and source IP
     * @throws BadRequestException if a CLIENT user type has a source IP address
     */
    void validateClientSourceIp(UserRequestDto userRequestDto);

    /**
     * Validates SUB_CLIENT user creation rules including source IP and parent relationships.
     *
     * <p>Validation rules:
     *
     * <ul>
     *   <li>SUB_CLIENT must always have a source IP address
     *   <li>ADMIN creating SUB_CLIENT must provide a parentId
     *   <li>CLIENT creating SUB_CLIENT cannot specify parentId (auto-assigned to self)
     * </ul>
     *
     * @param userRequestDto the user request data for the SUB_CLIENT being created
     * @param requester the user context of the requester creating the SUB_CLIENT
     * @throws BadRequestException if validation rules are violated
     */
    void validateSubClientCreation(UserRequestDto userRequestDto, UserContext requester);

    /**
     * Validates SUB_CLIENT user update rules.
     *
     * <p>Validation rule: If user type is SUB_CLIENT, a source IP address must be present.
     *
     * @param userRequestDto the user request data for the SUB_CLIENT being updated
     * @throws BadRequestException if SUB_CLIENT user type does not have a source IP address
     */
    void validateSubClientUpdate(UserRequestDto userRequestDto);
}
