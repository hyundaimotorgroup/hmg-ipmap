package com.hmg.ipmap.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CaffeineConfig {

    private final CaffeineProperties caffeineProperties;

    @Value("${cache.caffeine.max-size:100000}")
    private long maxSize;

    @Value("${cache.caffeine.initial-capacity:1000}")
    private int initialCapacity;

    @Value("${cache.caffeine.expire-after-write:5m}")
    private Duration expireAfterWrite;

    public CaffeineConfig(CaffeineProperties caffeineProperties) {
        this.caffeineProperties = caffeineProperties;
    }

    @Bean(name = "caffeineCacheManager")
    public CacheManager cacheManager() {
        if (!caffeineProperties.isEnabled()) {
            return new NoOpCacheManager();
        }
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .initialCapacity(initialCapacity)
                        .expireAfterWrite(expireAfterWrite));
        return cacheManager;
    }
}
