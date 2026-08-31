package com.hmg.ipmap.common.redis;

import java.time.Duration;

/**
 * Distributed lock service for coordinating access across multiple application instances.
 *
 * <p>Implementations use Redis (or compatible stores) to provide mutual exclusion. Lock ownership
 * is tracked per-thread; only the acquiring thread may release or renew a lock.
 */
public interface DistributedLockService {

    /**
     * Try to acquire a distributed lock, using the configured default lease time.
     *
     * @param lockKey the lock key (without prefix)
     * @param waitTime maximum duration to wait for the lock
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    boolean tryLock(String lockKey, Duration waitTime);

    /**
     * Try to acquire a distributed lock with an explicit lease time. Use this overload when the
     * lock must be held longer than the default lease (e.g., long-running batch steps).
     *
     * @param lockKey the lock key (without prefix)
     * @param waitTime maximum duration to wait for the lock
     * @param leaseTime TTL of the lock; should cover the expected hold duration
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime);

    /**
     * Extends the TTL of a lock that is already held by the current thread. Used as a heartbeat by
     * long-running steps so the lock does not expire while the leader is still active.
     *
     * <p>Renewal is best-effort: if the lease has already expired and another instance re-acquired
     * the lock, the renewal is skipped. Callers should treat a {@code false} return as a signal
     * that leadership was lost.
     *
     * @param lockKey the lock key (without prefix)
     * @param leaseTime new TTL to apply
     * @return {@code true} if the TTL was extended, {@code false} if the lock is no longer held by
     *     this thread
     */
    boolean renewLock(String lockKey, Duration leaseTime);

    /**
     * Release the distributed lock. Only releases if the current thread holds the lock (verified by
     * token).
     *
     * @param lockKey the lock key (without prefix)
     */
    void unlock(String lockKey);
}
