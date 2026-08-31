package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.common.util.UuidUtil;
import com.hmg.ipmap.ipmapping.exception.UnauthorizeException;
import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;
import com.hmg.ipmap.user.exception.ApiKeyUnauthorizeException;
import com.hmg.ipmap.user.exception.UserAlreadyExistException;
import com.hmg.ipmap.user.exception.UserNotFoundException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserValidationService validationService;
    private final UserSearchService searchService;

    @Value("${app.data-provider:default}")
    private String dataProviderProperty;

    public static final String USER_NOT_FOUND = "User not found";

    private static final String USER_NOT_FOUND_WITH_SOURCE_IP =
            "User not found for the given source IP";

    private static final String USER_WITH_GIVEN_ID_NOT_FOUND = "User with given ID not found";

    @Cacheable(
            value = "users",
            key = "'byApiKeyParentNull:' + #apiKey",
            cacheManager = "caffeineCacheManager")
    @Override
    public UserEntity findByApiKeyAndParentIsNull(String apiKey) {
        return userRepository
                .findByApiKeyAndParentIsNull(apiKey)
                .orElseThrow(() -> new ApiKeyUnauthorizeException(USER_NOT_FOUND));
    }

    @Cacheable(
            value = "users",
            key = "T(java.lang.String).format('%s|%s', #apiKey, #sourceIp)",
            cacheManager = "caffeineCacheManager")
    @Override
    public UserEntity findByApiKeyAndSourceIp(String apiKey, String sourceIp) {
        log.info("Fetching user from DB by sourceIp: {}", sourceIp);

        return userRepository
                .findByApiKeyAndSourceIp(apiKey, sourceIp)
                .orElseThrow(() -> new ApiKeyUnauthorizeException(USER_NOT_FOUND_WITH_SOURCE_IP));
    }

    @Override
    public PaginationResponse<UserResponseDto> searchWithPagination(PaginationRequest req) {
        return searchService.searchWithPagination(req);
    }

    @Override
    public void checkUserAccess(UserContext requester, UserEntity targetUser) {
        // Admin users have access to all resources
        if (requester.userType().equals(UserType.ADMIN)) {
            return;
        }

        // Users can always access their own data
        if (Objects.equals(requester.id(), targetUser.getId())) {
            return;
        }

        // Check if target user is a sub-client of the requester
        boolean isTargetSubClientOfRequester =
                (targetUser.getParent() != null
                        && Objects.equals(targetUser.getParent().getId(), requester.id()));

        // Client users can access their sub-clients
        if (isTargetSubClientOfRequester) {
            return;
        }
        throw new UnauthorizeException("User does not have privileges to access this resource.");
    }

    @Override
    public UserEntity getEntityById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    }

    @Override
    public UserResponseDto findById(Long targetUserId) {
        UserEntity targetUser =
                userRepository
                        .findById(targetUserId)
                        .orElseThrow(() -> new UserNotFoundException(USER_WITH_GIVEN_ID_NOT_FOUND));

        checkUserAccess(UserContextHolder.get(), targetUser);

        return userMapper.toDto(targetUser);
    }

    @Transactional
    @CacheEvict(
            value = {"users", "userList"},
            allEntries = true,
            cacheManager = "caffeineCacheManager")
    @Override
    public UserResponseDto create(UserRequestDto userRequestDto) {
        UserContext requester = UserContextHolder.get();

        // Validate all creation rules
        validationService.validateUserCreationPermissions(requester, userRequestDto.userType());
        validationService.validateClientSourceIp(userRequestDto);

        if (userRequestDto.userType() == UserType.SUB_CLIENT) {
            validationService.validateSubClientCreation(userRequestDto, requester);
        }

        validateUserNameUniqueness(userRequestDto.name());

        UserEntity user = buildNewUser(userRequestDto, requester);
        UserEntity saved = userRepository.save(user);

        return buildUserResponse(saved);
    }

    @Transactional
    @CacheEvict(
            value = {"users", "userList"},
            allEntries = true,
            cacheManager = "caffeineCacheManager")
    @Override
    public UserResponseDto update(Long id, UserRequestDto userRequestDto) {
        UserEntity existing =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        checkUserAccess(UserContextHolder.get(), existing);

        // Validate update rules
        validationService.validateClientSourceIp(userRequestDto);
        validationService.validateSubClientUpdate(userRequestDto);

        validateUserNameUniquenessForUpdate(userRequestDto.name(), id);

        updateUserFields(existing, userRequestDto);
        UserEntity updated = userRepository.save(existing);

        return buildUserResponse(updated);
    }

    @Transactional
    @CacheEvict(
            value = {"users", "userList"},
            allEntries = true,
            cacheManager = "caffeineCacheManager")
    @Override
    public void delete(Long id) {
        UserEntity existing =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        checkUserAccess(UserContextHolder.get(), existing);
        userRepository.delete(existing);
    }

    private void validateUserNameUniqueness(String name) {
        if (userRepository.findByName(name).isPresent()) {
            throw new UserAlreadyExistException("Username already exists: " + name);
        }
    }

    private void validateUserNameUniquenessForUpdate(String name, Long userId) {
        Optional<UserEntity> existNameOpt = userRepository.findByName(name);
        if (existNameOpt.isPresent() && !existNameOpt.get().getId().equals(userId)) {
            throw new UserAlreadyExistException("Username already exists: " + name);
        }
    }

    private UserEntity buildNewUser(UserRequestDto userRequestDto, UserContext requester) {
        UserEntity user = userMapper.toEntity(userRequestDto);

        user.setApiKey(UuidUtil.generateUuid());

        if (user.getResponseTemplate() == null) {
            user.setResponseTemplate(resolveDefaultResponseTemplate());
        }

        if (userRequestDto.parentId() != null) {
            setParentUser(user, userRequestDto.parentId());
        } else if (requester.userType() == UserType.CLIENT
                && userRequestDto.userType() == UserType.SUB_CLIENT) {
            setParentUser(user, requester.id());
        }

        return user;
    }

    private void setParentUser(UserEntity user, Long parentId) {
        UserEntity userParent =
                userRepository
                        .findById(parentId)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
        user.setParent(userParent);
        user.setApiKey(userParent.getApiKey());
    }

    private void updateUserFields(UserEntity existing, UserRequestDto userRequestDto) {
        existing.setName(userRequestDto.name());
        existing.setUserType(userRequestDto.userType());
        existing.setResponseTemplate(
                Optional.ofNullable(userRequestDto.responseTemplate())
                        .orElseGet(this::resolveDefaultResponseTemplate));

        if (userRequestDto.userType() == UserType.CLIENT) {
            existing.setSourceIp(null);
        }

        if (userRequestDto.parentId() != null && userRequestDto.parentId() > 0) {
            existing.setParent(userRepository.findById(userRequestDto.parentId()).orElse(null));
        }
    }

    private UserResponseDto buildUserResponse(UserEntity user) {
        UserResponseDto userDtoResponse = userMapper.toDto(user);

        if (user.getParent() != null) {
            userDtoResponse.setParentId(user.getParent().getId());
        }

        return userDtoResponse;
    }

    private UserResponseTemplateEnum resolveDefaultResponseTemplate() {
        return UserResponseTemplateEnum.valueOf(dataProviderProperty.toUpperCase());
    }
}
