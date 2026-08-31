package com.hmg.ipmap.cache.dto;

import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CacheOperationError {
    private Map<String, String> data;
    private String errorMessage;
    private CacheOpsAction action;
}
