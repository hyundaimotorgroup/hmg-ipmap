package com.hmg.ipmap.common.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${redisson.mode:single}")
    private String redissonMode;

    @Value("${redisson.address}")
    private String redissonSingleAddress;

    @Value("${redisson.cluster.nodes:}")
    private String clusterNodes;

    @Value("${redisson.username:#{null}}")
    private String redissonUsername;

    @Value("${redisson.password:#{null}}")
    private String redissonPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        log.info("Valkey in mode : {}", redissonMode);

        config.setUsername(redissonUsername);
        config.setPassword(redissonPassword);

        if ("cluster".equalsIgnoreCase(redissonMode)) {
            ClusterServersConfig clusterConfig = config.useClusterServers();

            for (String node : clusterNodes.split(",")) {
                clusterConfig.addNodeAddress(node.trim());
            }

        } else {
            // --- SINGLE SERVER MODE ---
            SingleServerConfig single = config.useSingleServer();
            single.setAddress(redissonSingleAddress);
        }

        config.setUseScriptCache(false);

        return Redisson.create(config);
    }

    @Primary
    @Bean(name = "redisCacheManager")
    public CacheManager cacheManager(RedissonClient redissonClient) {
        return new RedissonSpringCacheManager(redissonClient);
    }

    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder().build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        template.afterPropertiesSet();

        return template;
    }

    @Bean(name = "redisCacheTemplate")
    public RedisTemplate<String, String> redisCacheTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();

        return template;
    }
}
