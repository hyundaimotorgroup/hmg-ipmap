package com.hmg.ipmap.common.redis;

import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Distributed lock service using Redis SET NX command. This implementation uses simple Redis
 * commands (SET with NX flag) which do not require EVAL, making it compatible with Valkey/Redis
 * environments where EVAL is prohibited.
 */
@Slf4j
@Service
public class RedisLockService implements DistributedLockService {

    private static final String LOCK_PREFIX = "lock:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ThreadLocal<String> lockTokenHolder = new ThreadLocal<>();

    @Value("${cache-sync.job.lock.lease-time:30s}")
    private Duration lockExpiry;

    public RedisLockService(
            @Qualifier("redisCacheTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, Duration waitTime) {
        return tryLock(lockKey, waitTime, lockExpiry);
    }

    @Override
    public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockToken = UUID.randomUUID().toString();
        long endTime = System.currentTimeMillis() + waitTime.toMillis();

        do {
            Boolean acquired =
                    redisTemplate.opsForValue().setIfAbsent(fullKey, lockToken, leaseTime);

            if (Boolean.TRUE.equals(acquired)) {
                lockTokenHolder.set(lockToken);
                log.trace("Successfully acquired lock for key: {}", lockKey);
                return true;
            }

            if (System.currentTimeMillis() >= endTime) {
                break;
            }

            // Wait a bit before retrying
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for lock", e);
                return false;
            }
        } while (true);

        log.trace("Failed to acquire lock for key: {} within {}", lockKey, waitTime);
        return false;
    }

    @Override
    public boolean renewLock(String lockKey, Duration leaseTime) {
        String fullKey = LOCK_PREFIX + lockKey;
        String token = lockTokenHolder.get();
        if (token == null) {
            // This thread is not the leader — no-op.
            return false;
        }
        String currentValue = redisTemplate.opsForValue().get(fullKey);
        if (token.equals(currentValue)) {
            redisTemplate.expire(fullKey, leaseTime);
            log.trace("Renewed lock for key: {} (leaseTime: {})", lockKey, leaseTime);
            return true;
        }
        log.warn(
                "Lock token mismatch during renewal for key: {} — lock may have expired and been"
                        + " re-acquired by another instance",
                lockKey);
        return false;
    }

    @Override
    public void unlock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockToken = lockTokenHolder.get();

        if (lockToken == null) {
            log.warn("No lock token found for key: {} - lock was not held by this thread", lockKey);
            return;
        }

        try {
            // Get the current value and delete only if it matches our token
            String currentValue = redisTemplate.opsForValue().get(fullKey);

            if (lockToken.equals(currentValue)) {
                redisTemplate.delete(fullKey);
                log.trace("Successfully released lock for key: {}", lockKey);
            } else {
                log.warn(
                        "Lock token mismatch for key: {} - lock may have expired or been taken by another thread",
                        lockKey);
            }
        } finally {
            lockTokenHolder.remove();
        }
    }
}
