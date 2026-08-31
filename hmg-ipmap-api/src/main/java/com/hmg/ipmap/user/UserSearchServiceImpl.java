package com.hmg.ipmap.user;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserResponseDto;
import com.hmg.ipmap.user.dto.UserSearchSpecification;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchServiceImpl implements UserSearchService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public PaginationResponse<UserResponseDto> searchWithPagination(PaginationRequest req) {
        log.trace("UserContextHolder.get() : {}", UserContextHolder.get());

        UserType currentUserType = UserContextHolder.get().userType();
        Long currentUserId = UserContextHolder.get().id();

        Specification<UserEntity> spec = buildSearchSpecification(currentUserType, currentUserId);
        log.trace("spec={}", spec);

        Pageable pageable = PageRequest.of(req.pageOrDefault(), req.sizeOrDefault());
        log.trace("pageable={}", pageable);

        Page<UserEntity> userEntities = userRepository.findAll(spec, pageable);
        Page<UserResponseDto> pageDto = userEntities.map(userMapper::toDto);

        return new PaginationResponse<>(
                pageDto.getContent(),
                pageDto.isLast(),
                pageDto.getTotalElements(),
                pageDto.getTotalPages(),
                pageDto.isFirst(),
                pageDto.getSize(),
                pageDto.getNumber(),
                pageDto.getNumberOfElements(),
                pageDto.isEmpty());
    }

    private Specification<UserEntity> buildSearchSpecification(
            UserType currentUserType, Long currentUserId) {
        return switch (currentUserType) {
            case SUB_CLIENT ->
                    UserSearchSpecification.byFilters(
                            currentUserId,
                            Set.of(UserType.SUB_CLIENT),
                            Set.of(currentUserId),
                            false);
            case CLIENT ->
                    UserSearchSpecification.byFilters(
                            currentUserId,
                            Set.of(UserType.CLIENT, UserType.SUB_CLIENT),
                            Set.of(currentUserId),
                            false);
            default ->
                    UserSearchSpecification.byFilters(
                            null,
                            Set.of(UserType.CLIENT, UserType.SUB_CLIENT, UserType.ADMIN),
                            Set.of(),
                            false);
        };
    }
}
