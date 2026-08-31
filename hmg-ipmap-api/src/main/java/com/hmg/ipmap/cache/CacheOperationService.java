package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;

public interface CacheOperationService {
    CacheOperationResponseDto updateCache(CacheOperationRequestDto cacheOperationRequestDto);
}
