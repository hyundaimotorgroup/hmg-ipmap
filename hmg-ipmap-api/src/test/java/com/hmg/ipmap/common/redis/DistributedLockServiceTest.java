package com.hmg.ipmap.common.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    private static final String LOCK_KEY = "test-lock";
    private static final String FULL_LOCK_KEY = "lock:test-lock";

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        distributedLockService = new RedisLockService(redisTemplate);
        ReflectionTestUtils.setField(distributedLockService, "lockExpiry", Duration.ofSeconds(30));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryLock_WhenLockAvailable_ShouldAcquireLockOnFirstAttempt() {
        // Arrange
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        // Act
        boolean result = distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));

        // Assert
        assertTrue(result);

        // Verify Redis interaction
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations)
                .setIfAbsent(keyCaptor.capture(), tokenCaptor.capture(), durationCaptor.capture());

        assertEquals(FULL_LOCK_KEY, keyCaptor.getValue());
        assertNotNull(tokenCaptor.getValue()); // UUID token should be generated
        assertEquals(Duration.ofSeconds(30), durationCaptor.getValue());
    }

    @Test
    void tryLock_WhenLockNotAvailableInitiallyThenAvailable_ShouldAcquireAfterRetry() {
        // Arrange - First attempt fails, second succeeds
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false)
                .thenReturn(true);

        // Act
        boolean result = distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));

        // Assert
        assertTrue(result);
        verify(valueOperations, atLeast(2))
                .setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class));
    }

    @Test
    void tryLock_WhenTimeoutExpires_ShouldReturnFalse() {
        // Arrange - Always return false (lock not available)
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        // Act - Use short timeout to speed up test
        boolean result = distributedLockService.tryLock(LOCK_KEY, Duration.ofMillis(250));

        // Assert
        assertFalse(result);
        verify(valueOperations, atLeast(2))
                .setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class));
    }

    @Test
    void tryLock_WhenInterrupted_ShouldReturnFalseAndRestoreInterruptFlag() {
        // Arrange
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        Thread.currentThread().interrupt(); // Interrupt the thread

        // Act
        boolean result = distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(5));

        // Assert
        assertFalse(result);
        assertTrue(Thread.interrupted()); // Verify interrupt flag was restored (and clear it)
    }

    @Test
    void tryLock_ShouldApplyLockPrefix() {
        // Arrange
        String customKey = "custom-lock-key";
        String expectedFullKey = "lock:custom-lock-key";

        when(valueOperations.setIfAbsent(eq(expectedFullKey), anyString(), any(Duration.class)))
                .thenReturn(true);

        // Act
        distributedLockService.tryLock(customKey, Duration.ofSeconds(1));

        // Assert
        verify(valueOperations).setIfAbsent(eq(expectedFullKey), anyString(), any(Duration.class));
    }

    @Test
    void tryLock_ShouldUseConfiguredLockExpiry() {
        // Arrange
        Duration customExpiry = Duration.ofMinutes(5);
        ReflectionTestUtils.setField(distributedLockService, "lockExpiry", customExpiry);

        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        // Act
        distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));

        // Assert
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations)
                .setIfAbsent(eq(FULL_LOCK_KEY), anyString(), durationCaptor.capture());

        assertEquals(customExpiry, durationCaptor.getValue());
    }

    @Test
    void tryLock_WhenRedisReturnsNull_ShouldRetryUntilTimeout() {
        // Arrange - Simulate Redis returning null (unusual but possible)
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(null);

        // Act
        boolean result = distributedLockService.tryLock(LOCK_KEY, Duration.ofMillis(250));

        // Assert
        assertFalse(result);
    }

    @Test
    void unlock_WhenTokenMatches_ShouldDeleteLock() {
        // Arrange - First acquire lock
        String capturedToken = captureLockToken();

        // Mock Redis get to return the same token
        when(valueOperations.get(FULL_LOCK_KEY)).thenReturn(capturedToken);

        // Act
        distributedLockService.unlock(LOCK_KEY);

        // Assert
        verify(valueOperations).get(FULL_LOCK_KEY);
        verify(redisTemplate).delete(FULL_LOCK_KEY);
    }

    @Test
    void unlock_WhenTokenDoesNotMatch_ShouldNotDeleteLock() {
        // Arrange - Acquire lock first
        captureLockToken();

        // Mock Redis get to return different token (lock taken by another thread)
        when(valueOperations.get(FULL_LOCK_KEY)).thenReturn("different-token");

        // Act
        distributedLockService.unlock(LOCK_KEY);

        // Assert
        verify(valueOperations).get(FULL_LOCK_KEY);
        verify(redisTemplate, never()).delete(FULL_LOCK_KEY);
    }

    @Test
    void unlock_WhenRedisReturnsNull_ShouldNotDeleteLock() {
        // Arrange - Acquire lock first
        captureLockToken();

        // Mock Redis get to return null (lock already expired)
        when(valueOperations.get(FULL_LOCK_KEY)).thenReturn(null);

        // Act
        distributedLockService.unlock(LOCK_KEY);

        // Assert
        verify(valueOperations).get(FULL_LOCK_KEY);
        verify(redisTemplate, never()).delete(FULL_LOCK_KEY);
    }

    @Test
    void unlock_ShouldAlwaysRemoveTokenFromThreadLocal() {
        // Arrange - Acquire lock first
        captureLockToken();

        // Mock Redis to throw exception during unlock
        when(valueOperations.get(FULL_LOCK_KEY)).thenThrow(new RuntimeException("Redis error"));

        // Act & Assert - Exception should propagate but cleanup should happen
        assertThrows(RuntimeException.class, () -> distributedLockService.unlock(LOCK_KEY));

        // Verify ThreadLocal was cleaned up by trying to unlock again
        // If ThreadLocal was cleaned, this should log warning about no token
        distributedLockService.unlock(LOCK_KEY);
        verify(valueOperations, times(1)).get(FULL_LOCK_KEY); // Only called once (not twice)
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryLock_ShouldGenerateUniqueLockTokens() {
        // Arrange
        ArgumentCaptor<String> tokenCaptor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor2 = ArgumentCaptor.forClass(String.class);

        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        // Act - Acquire lock, release, then acquire again
        distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(valueOperations)
                .setIfAbsent(eq(FULL_LOCK_KEY), tokenCaptor1.capture(), any(Duration.class));

        // Clean up for second attempt
        ReflectionTestUtils.setField(
                distributedLockService, "lockTokenHolder", ThreadLocal.withInitial(() -> null));
        reset(valueOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));
        verify(valueOperations)
                .setIfAbsent(eq(FULL_LOCK_KEY), tokenCaptor2.capture(), any(Duration.class));

        // Assert - Tokens should be different
        assertNotEquals(tokenCaptor1.getValue(), tokenCaptor2.getValue());
    }

    @Test
    void tryLock_MultipleRetries_ShouldRespectWaitTime() {
        // Arrange
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        long startTime = System.currentTimeMillis();

        // Act
        Duration waitTime = Duration.ofMillis(500);
        boolean result = distributedLockService.tryLock(LOCK_KEY, waitTime);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Assert
        assertFalse(result);
        // Elapsed time should be approximately equal to wait time (with some tolerance)
        assertTrue(
                elapsedTime >= waitTime.toMillis() - 50,
                "Elapsed time should be at least wait time");
        assertTrue(
                elapsedTime < waitTime.toMillis() + 500,
                "Elapsed time should not exceed wait time by much");
    }

    @Test
    void tryLock_WhenLockAcquired_ShouldStoreTokenInThreadLocal() {
        // Arrange
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        // Act
        distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));

        // Capture the token that was sent to Redis
        verify(valueOperations)
                .setIfAbsent(eq(FULL_LOCK_KEY), tokenCaptor.capture(), any(Duration.class));

        // Now unlock and verify the same token is used
        when(valueOperations.get(FULL_LOCK_KEY)).thenReturn(tokenCaptor.getValue());
        distributedLockService.unlock(LOCK_KEY);

        // Assert - The unlock should succeed (token matches)
        verify(redisTemplate).delete(FULL_LOCK_KEY);
    }

    /**
     * Helper method to acquire lock and capture the generated token
     *
     * @return The captured lock token
     */
    private String captureLockToken() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(valueOperations.setIfAbsent(eq(FULL_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        distributedLockService.tryLock(LOCK_KEY, Duration.ofSeconds(1));

        verify(valueOperations)
                .setIfAbsent(eq(FULL_LOCK_KEY), tokenCaptor.capture(), any(Duration.class));

        return tokenCaptor.getValue();
    }
}
