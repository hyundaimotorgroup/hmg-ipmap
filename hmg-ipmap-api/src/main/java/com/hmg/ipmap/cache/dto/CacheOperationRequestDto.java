package com.hmg.ipmap.cache.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CacheOperationRequestDto {
    @NotEmpty
    @Size(max = 500)
    private List<CacheOperation> operations;
}
