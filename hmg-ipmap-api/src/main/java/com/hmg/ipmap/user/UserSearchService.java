package com.hmg.ipmap.user;

import com.hmg.ipmap.common.pagination.PaginationRequest;
import com.hmg.ipmap.common.pagination.PaginationResponse;
import com.hmg.ipmap.user.dto.UserResponseDto;

public interface UserSearchService {
    PaginationResponse<UserResponseDto> searchWithPagination(PaginationRequest req);
}
