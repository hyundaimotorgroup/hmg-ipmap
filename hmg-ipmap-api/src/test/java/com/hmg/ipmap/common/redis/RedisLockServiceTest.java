package com.hmg.ipmap.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisLockService Tests")
class RedisLockServiceTest {

    private static final String LOCK_KEY = "test-lock";
    private static final String FULL_LOCK_KEY = "lock:" + LOCK_KEY;
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisLockService redisLockService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisLockService = new RedisLockService(redisTemplate);
        ReflectionTestUtils.setField(redisLockService, "lockExpiry", DEFAULT_LEASE);
    }

    @Nested
    @DisplayName("tryLock with waitTime=ZERO")
    class TryLockWithZeroWaitTime {

        @Test
        @DisplayName("Given lock is free, attempts exactly once and returns true")
        void returnsTrue_WhenLockFreeAndWaitTimeIsZero() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            boolean result = redisLockService.tryLock(LOCK_KEY, Duration.ZERO);

            assertThat(result).isTrue();
            verify(valueOperations, times(1))
                    .setIfAbsent(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Given lock is held, attempts exactly once and returns false without retrying")
        void returnsFalse_WhenLockHeldAndWaitTimeIsZero() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);

            boolean result = redisLockService.tryLock(LOCK_KEY, Duration.ZERO);

            assertThat(result).isFalse();
            // Must attempt exactly once — not zero times (old bug) and not multiple times
            verify(valueOperations, times(1))
                    .setIfAbsent(anyString(), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("tryLock with positive waitTime")
    class TryLockWithPositiveWaitTime {

        @Test
        @DisplayName("Given lock is free immediately, returns true on first attempt")
        void returnsTrue_WhenLockFreeImmediately() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            boolean result = redisLockService.tryLock(LOCK_KEY, Duration.ofSeconds(5));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Given lock is held then released, returns true after retry")
        void returnsTrue_WhenLockReleasedBeforeDeadline() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false)
                    .thenReturn(true);

            boolean result = redisLockService.tryLock(LOCK_KEY, Duration.ofMillis(500));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Given lock uses the correct Redis key prefix")
        void usesCorrectKeyPrefix() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            redisLockService.tryLock(LOCK_KEY, Duration.ZERO);

            verify(valueOperations)
                    .setIfAbsent(
                            org.mockito.ArgumentMatchers.eq(FULL_LOCK_KEY),
                            anyString(),
                            any(Duration.class));
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("Given lock held by current thread, deletes the Redis key")
        void deletesKey_WhenLockHeldByCurrentThread() {
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            when(valueOperations.setIfAbsent(
                            anyString(), tokenCaptor.capture(), any(Duration.class)))
                    .thenReturn(true);

            redisLockService.tryLock(LOCK_KEY, Duration.ZERO);
            when(valueOperations.get(FULL_LOCK_KEY)).thenReturn(tokenCaptor.getValue());

            redisLockService.unlock(LOCK_KEY);

            verify(redisTemplate).delete(FULL_LOCK_KEY);
        }

        @Test
        @DisplayName("Given no lock held by current thread, does not delete key")
        void doesNotDeleteKey_WhenLockNotHeldByCurrentThread() {
            redisLockService.unlock(LOCK_KEY);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("Given lock token mismatch, does not delete key")
        void doesNotDeleteKey_WhenTokenMismatch() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            redisLockService.tryLock(LOCK_KEY, Duration.ZERO);
            when(valueOperations.get(FULL_LOCK_KEY)).thenReturn("different-token");

            redisLockService.unlock(LOCK_KEY);

            verify(redisTemplate, never()).delete(anyString());
        }
    }
}
