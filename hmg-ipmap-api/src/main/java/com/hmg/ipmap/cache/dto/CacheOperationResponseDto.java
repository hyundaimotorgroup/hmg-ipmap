package com.hmg.ipmap.cache.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CacheOperationResponseDto {
    private int successCount;
    private int errorCount;
    private List<CacheOperationError> errors;
}
