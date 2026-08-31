package com.hmg.ipmap.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.CacheOperationError;
import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.dto.CacheOperationResponseDto;
import com.hmg.ipmap.cache.enums.CacheOpsAction;
import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class CacheOperationServiceTest {
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private CacheOperationValidator cacheOperationValidator;
    @Mock private IpNotationFactory ipNotationFactory;
    @Mock private IpSpanProperties ipSpanProperties;

    @Mock private HashOperations<String, Object, Object> hashOperations;

    @Mock private ZSetOperations<String, String> zSetOperations;

    @Mock private ValueOperations<String, String> stringOperations;

    private CacheOperationService cacheOperationService;

    @BeforeEach
    void setUp() {
        cacheOperationService =
                new CacheOperationServiceImpl(
                        redisTemplate,
                        cacheOperationValidator,
                        ipNotationFactory,
                        ipSpanProperties);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(stringOperations);
        lenient()
                .when(ipNotationFactory.mapIpToSubnet(anyString(), anyInt()))
                .thenReturn("192.168.1.0/24");
    }

    @Test
    void testUpdateCache_WithStringHashCacheOperations_ShouldReturnSuccessResponse() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.LOCATION_UPDATE,
                        Map.of("id", "1", "pub_id", "JP", "name", "Seoul", "user_id", "1"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        doNothing().when(stringOperations).set(anyString(), anyString());

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verify(stringOperations).set(anyString(), anyString());
    }

    @Test
    void testUpdateCache_WithValidSortedSetOperations_ShouldReturnSuccessResponse() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.IP_SPAN_UPDATE,
                        Map.of(
                                "ip_lower",
                                "3232235777",
                                "ip_upper",
                                "3232236031",
                                "locationId",
                                "1",
                                "user_id",
                                "1",
                                "scope",
                                "GLOBAL"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void testUpdateCache_WithStringCacheDeleteOperation_ShouldDeleteSuccessfully() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.LOCATION_DELETE,
                        Map.of("id", "1", "pub_id", "JP", "user_id", "1"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void testUpdateCache_WithSortedSetDeleteOperation_ShouldDeleteSuccessfully() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.IP_SPAN_DELETE,
                        Map.of(
                                "ip_lower",
                                "3232235777",
                                "ip_upper",
                                "3232236031",
                                "user_id",
                                "1",
                                "scope",
                                "GLOBAL"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(zSetOperations.remove(anyString(), anyString())).thenReturn(1L);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verify(zSetOperations).remove(anyString(), anyString());
    }

    @Test
    void testUpdateCache_WithStringCacheDeleteOperationNotFound_ShouldReturnError() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.LOCATION_DELETE,
                        Map.of("id", "1", "pub_id", "JP", "user_id", "1"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(redisTemplate.delete(anyString())).thenReturn(false);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getErrorMessage())
                .isEqualTo("Failed to delete. Data not found");
    }

    @Test
    void testUpdateCache_WithSortedSetDeleteOperationNotFound_ShouldReturnError() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.IP_SPAN_DELETE,
                        Map.of(
                                "ip_lower",
                                "3232235777",
                                "ip_upper",
                                "3232236031",
                                "user_id",
                                "1",
                                "scope",
                                "GLOBAL"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(zSetOperations.remove(anyString(), anyString())).thenReturn(null);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getErrorMessage())
                .isEqualTo("Failed to delete. Data not found");
    }

    @Test
    void testUpdateCache_WithSortedSetOperationFailure_ShouldReturnError() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.IP_SPAN_UPDATE,
                        Map.of(
                                "ip_lower",
                                "3232235777",
                                "ip_upper",
                                "3232236031",
                                "locationId",
                                "1",
                                "user_id",
                                "1",
                                "scope",
                                "GLOBAL"));

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(false);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getErrorMessage()).isEqualTo("update failed");
    }

    @Test
    void testUpdateCache_WithValidationErrors_ShouldReturnErrorsFromValidator() {
        // Given
        CacheOperationRequestDto requestDto =
                createCacheOperationRequestDto(
                        CacheOpsAction.LOCATION_UPDATE,
                        Map.of("id", "1", "pub_id", "SL", "name", "Seoul", "user_id", "1"));

        List<CacheOperationError> validationErrors =
                Collections.singletonList(
                        CacheOperationError.builder()
                                .action(CacheOpsAction.LOCATION_UPDATE)
                                .data(requestDto.getOperations().getFirst().getData())
                                .errorMessage("Validation failed")
                                .build());

        when(cacheOperationValidator.validate(any())).thenReturn(validationErrors);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getErrorMessage())
                .isEqualTo("Validation failed");
        verifyNoInteractions(zSetOperations);
        verifyNoInteractions(hashOperations);
    }

    //
    @Test
    void testUpdateCache_WithMixedOperations_ShouldHandleBothHashAndSortedSet() {
        // Given
        List<CacheOperation> operations =
                Arrays.asList(
                        createCacheOperation(
                                CacheOpsAction.LOCATION_UPDATE,
                                Map.of("id", "1", "pub_id", "SL", "name", "Seoul", "user_id", "1")),
                        createCacheOperation(
                                CacheOpsAction.IP_SPAN_UPDATE,
                                Map.of(
                                        "ip_lower",
                                        "3232235777",
                                        "ip_upper",
                                        "3232236031",
                                        "locationId",
                                        "1",
                                        "user_id",
                                        "1",
                                        "scope",
                                        "GLOBAL")));

        CacheOperationRequestDto requestDto = new CacheOperationRequestDto();
        requestDto.setOperations(operations);

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        doNothing().when(stringOperations).set(anyString(), anyString());
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verify(stringOperations).set(anyString(), anyString());
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void testUpdateCache_WithMultipleOperationsPartialFailure_ShouldReturnCorrectCounts() {
        // Given
        List<CacheOperation> operations =
                Arrays.asList(
                        createCacheOperation(
                                CacheOpsAction.LOCATION_UPDATE,
                                Map.of("id", "1", "pub_id", "SL", "name", "Seoul", "user_id", "1")),
                        createCacheOperation(
                                CacheOpsAction.LOCATION_UPDATE,
                                Map.of(
                                        "id", "2", "pub_id", "BSN", "name", "Busan", "user_id",
                                        "1")),
                        createCacheOperation(
                                CacheOpsAction.LOCATION_DELETE,
                                Map.of("id", "1", "pub_id", "JP", "user_id", "1")));

        CacheOperationRequestDto requestDto = new CacheOperationRequestDto();
        requestDto.setOperations(operations);

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());
        doNothing().when(stringOperations).set(anyString(), anyString());
        when(redisTemplate.delete(anyString())).thenReturn(eq(false)); // Delete fails

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getErrorMessage())
                .isEqualTo("Failed to delete. Data not found");
    }

    @Test
    void testUpdateCache_WithEmptyOperationsList_ShouldReturnZeroCountsAndNoErrors() {
        // Given
        CacheOperationRequestDto requestDto = new CacheOperationRequestDto();
        requestDto.setOperations(new ArrayList<>());

        when(cacheOperationValidator.validate(any())).thenReturn(new ArrayList<>());

        // When
        CacheOperationResponseDto response = cacheOperationService.updateCache(requestDto);

        // Then
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getErrors()).isEmpty();
        verifyNoInteractions(zSetOperations);
    }

    // Helper methods
    private CacheOperationRequestDto createCacheOperationRequestDto(
            CacheOpsAction action, Map<String, String> data) {
        CacheOperationRequestDto requestDto = new CacheOperationRequestDto();
        requestDto.setOperations(List.of(createCacheOperation(action, data)));
        return requestDto;
    }

    private CacheOperation createCacheOperation(CacheOpsAction action, Map<String, String> data) {
        CacheOperation operation = new CacheOperation();
        operation.setAction(action);
        operation.setData(data);
        return operation;
    }
}
