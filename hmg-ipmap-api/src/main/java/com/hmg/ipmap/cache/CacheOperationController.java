package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheOperationController {

    private final CacheOperationService cacheOperationService;

    @PostMapping("/ops")
    public ResponseEntity<CacheOperationResponseDto> cacheOperation(
            @Valid @RequestBody CacheOperationRequestDto cacheOperationRequestDto) {
        CacheOperationResponseDto cacheOperationResponseDto =
                cacheOperationService.updateCache(cacheOperationRequestDto);
        return ResponseEntity.ok().body(cacheOperationResponseDto);
    }
}
