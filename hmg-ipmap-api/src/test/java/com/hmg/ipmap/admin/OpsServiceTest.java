package com.hmg.ipmap.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hmg.ipmap.admin.dto.OpsRequestDto;
import com.hmg.ipmap.admin.dto.OpsResponseDto;
import com.hmg.ipmap.common.enums.OpsAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class OpsServiceTest {

    private OpsService opsService;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        opsService = new OpsServiceImpl(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFlushAllRedisKeys() {
        OpsRequestDto request = new OpsRequestDto();
        request.setAction(OpsAction.CACHE_FLUSH_ALL);

        OpsResponseDto response = opsService.decideOpsAction(request);

        assertEquals("Action executed successfully", response.getMessage());
        verify(redisTemplate, times(1)).execute((RedisCallback<Object>) any());
    }
}
