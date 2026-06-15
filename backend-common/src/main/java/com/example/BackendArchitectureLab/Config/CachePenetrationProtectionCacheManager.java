package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachePenetrationProtectionCacheManager extends RedisCacheManager {

    private static final Logger log = LoggerFactory.getLogger(CachePenetrationProtectionCacheManager.class);

    private final Map<String, Cache> wrappedCaches = new ConcurrentHashMap<>();

    private final StringRedisTemplate stringRedisTemplate;

    private final IBloomFilterService bloomFilterService;

    private final NullValueTtlProperties nullValueTtlProperties;

    private final RedissonClient redissonClient;

    public CachePenetrationProtectionCacheManager(
            RedisCacheWriter cacheWriter,
            RedisCacheConfiguration defaultCacheConfiguration,
            Map<String, RedisCacheConfiguration> initialCacheConfigurations,
            StringRedisTemplate stringRedisTemplate,
            IBloomFilterService bloomFilterService,
            NullValueTtlProperties nullValueTtlProperties,
            RedissonClient redissonClient) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations);
        this.stringRedisTemplate = stringRedisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.nullValueTtlProperties = nullValueTtlProperties;
        this.redissonClient = redissonClient;
    }

    @Override
    public Cache getCache(String name) {
        Cache existing = wrappedCaches.get(name);
        if (existing != null) {
            return existing;
        }

        Cache cache = super.getCache(name);
        if (cache instanceof RedisCache redisCache) {
            CachePenetrationProtectionCache wrapped = new CachePenetrationProtectionCache(
                    name, redisCache, stringRedisTemplate,
                    bloomFilterService, redissonClient,
                    nullValueTtlProperties.getNullTtl(name)
            );
            wrappedCaches.putIfAbsent(name, wrapped);
            return wrappedCaches.get(name);
        }
        return cache;
    }
}
